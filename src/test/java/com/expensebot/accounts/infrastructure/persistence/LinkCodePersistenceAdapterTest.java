package com.expensebot.accounts.infrastructure.persistence;

import com.expensebot.accounts.domain.LinkCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(LinkCodePersistenceAdapter.class)
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
}
