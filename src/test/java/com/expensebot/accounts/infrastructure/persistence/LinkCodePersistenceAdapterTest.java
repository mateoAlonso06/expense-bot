package com.expensebot.accounts.infrastructure.persistence;

import com.expensebot.accounts.domain.LinkCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(LinkCodePersistenceAdapter.class)
@Tag("persistence")
class LinkCodePersistenceAdapterTest {
    private static final Instant EXPIRY = Instant.parse("2026-11-10T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private LinkCodePersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void createTheUser() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profile (id, email) VALUES (?, ?)", userId, userId + "@example.com");
    }

    @Test
    void storesTheCodeUnusedAndWithItsExpiry() {
        var linkCode = LinkCode.create(userId, "hash-1", EXPIRY);

        adapter.save(linkCode);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM link_code WHERE id = ?", linkCode.getId());
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("code_hash")).isEqualTo("hash-1");
        assertThat(((java.sql.Timestamp) row.get("expires_at")).toInstant()).isEqualTo(EXPIRY);
        assertThat(row.get("used_at")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void rejectsTwoCodesWithTheSameHash() {
        adapter.save(LinkCode.create(userId, "same-hash", EXPIRY));

        assertThatThrownBy(() -> adapter.save(LinkCode.create(userId, "same-hash", EXPIRY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsACodeForAUserThatDoesNotExist() {
        assertThatThrownBy(() -> adapter.save(LinkCode.create(UUID.randomUUID(), "hash-2", EXPIRY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void consumesAnUnusedCodeAndReturnsItsOwner() {
        adapter.save(LinkCode.create(userId, "hash-3", EXPIRY));

        Optional<UUID> owner = adapter.consume("hash-3", EXPIRY.minusSeconds(1));

        assertThat(owner).contains(userId);
        assertThat(jdbc.queryForObject("SELECT used_at FROM link_code WHERE code_hash = 'hash-3'",
                java.sql.Timestamp.class).toInstant()).isEqualTo(EXPIRY.minusSeconds(1));
    }

    @Test
    void doesNotConsumeAnUnknownCode() {
        assertThat(adapter.consume("no-such-hash", EXPIRY.minusSeconds(1))).isEmpty();
    }

    @Test
    void doesNotConsumeAnExpiredCode() {
        adapter.save(LinkCode.create(userId, "hash-4", EXPIRY));

        assertThat(adapter.consume("hash-4", EXPIRY)).isEmpty(); // expiry is exclusive
        assertThat(jdbc.queryForObject("SELECT used_at FROM link_code WHERE code_hash = 'hash-4'",
                java.sql.Timestamp.class)).isNull();
    }

    @Test
    void doesNotConsumeACodeTwice() {
        adapter.save(LinkCode.create(userId, "hash-5", EXPIRY));
        adapter.consume("hash-5", EXPIRY.minusSeconds(60));

        assertThat(adapter.consume("hash-5", EXPIRY.minusSeconds(30))).isEmpty();
    }

    // Same recipe as ProcessedMessagePersistenceAdapterTest: no wrapping transaction, so every call commits on
    // its own and the database is what decides the winner.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void letsExactlyOneConcurrentAttemptConsumeTheCode() throws Exception {
        adapter.save(LinkCode.create(userId, "hash-6", EXPIRY));
        int threads = 8;
        var startSignal = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int winners = 0;
        try {
            List<Future<Optional<UUID>>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    startSignal.await();
                    return adapter.consume("hash-6", EXPIRY.minusSeconds(1));
                }));
            }
            startSignal.countDown();
            for (Future<Optional<UUID>> result : results) {
                if (result.get().isPresent()) { // rethrows if a thread failed
                    winners++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(winners).isEqualTo(1);
    }
}
