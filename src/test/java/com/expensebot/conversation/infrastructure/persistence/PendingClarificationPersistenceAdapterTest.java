package com.expensebot.conversation.infrastructure.persistence;

import com.expensebot.accounts.Channel;
import com.expensebot.conversation.domain.PendingClarification;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(PendingClarificationPersistenceAdapter.class)
@Tag("persistence")
class PendingClarificationPersistenceAdapterTest {
    private static final Instant EXPIRY = Instant.parse("2026-11-19T12:10:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private PendingClarificationPersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void createTheUser() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profile (id, email) VALUES (?, ?)", userId, userId + "@example.com");
    }

    private PendingClarification missing(String description) {
        return PendingClarification.missingAmount(userId, Channel.TELEGRAM, "gasté en " + description, description,
                "Alimentación", EXPIRY);
    }

    @Test
    void findsAMissingAmountAsItWasStored() {
        adapter.save(missing("almuerzo"));

        PendingClarification found = adapter.find(userId, Channel.TELEGRAM).orElseThrow();

        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getChannel()).isEqualTo(Channel.TELEGRAM);
        assertThat(found.getOriginalMessage()).isEqualTo("gasté en almuerzo");
        assertThat(found.getDescription()).isEqualTo("almuerzo");
        assertThat(found.getCategoryName()).isEqualTo("Alimentación");
        assertThat(found.getAmountCandidates()).isEmpty();
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRY);
    }

    @Test
    void keepsTheCandidatesInTheirOrder() {
        adapter.save(PendingClarification.ambiguousAmount(userId, Channel.TELEGRAM, "super 5000 o 3000,50", null,
                null, List.of(new BigDecimal("5000"), new BigDecimal("3000.50")), EXPIRY));

        PendingClarification found = adapter.find(userId, Channel.TELEGRAM).orElseThrow();

        assertThat(found.getAmountCandidates()).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("5000"), new BigDecimal("3000.50"));
        assertThat(found.getDescription()).isNull();
        assertThat(found.getCategoryName()).isNull();
    }

    @Test
    void askingAgainReplacesTheConversationsQuestion() {
        adapter.save(missing("almuerzo"));
        adapter.save(missing("nafta"));

        assertThat(adapter.find(userId, Channel.TELEGRAM).orElseThrow().getDescription()).isEqualTo("nafta");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pending_clarification WHERE user_id = ?",
                Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void eachChannelIsItsOwnConversation() {
        adapter.save(missing("almuerzo"));

        assertThat(adapter.find(userId, Channel.WHATSAPP)).isEmpty();
        assertThat(adapter.find(UUID.randomUUID(), Channel.TELEGRAM)).isEmpty();
    }

    @Test
    void deletesTheQuestion() {
        adapter.save(missing("almuerzo"));

        adapter.delete(userId, Channel.TELEGRAM);

        assertThat(adapter.find(userId, Channel.TELEGRAM)).isEmpty();
    }

    @Test
    void rejectsAQuestionForAUserThatDoesNotExist() {
        assertThatThrownBy(() -> adapter.save(PendingClarification.missingAmount(UUID.randomUUID(),
                Channel.TELEGRAM, "m", "d", null, EXPIRY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
