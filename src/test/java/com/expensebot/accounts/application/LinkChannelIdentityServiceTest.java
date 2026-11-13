package com.expensebot.accounts.application;

import com.expensebot.accounts.Channel;
import com.expensebot.accounts.LinkResult;
import com.expensebot.accounts.domain.LinkCode;
import com.expensebot.accounts.infrastructure.persistence.ChannelIdentityPersistenceAdapter;
import com.expensebot.accounts.infrastructure.persistence.LinkCodePersistenceAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Runs against a real database and without a wrapping test transaction: what these tests check is exactly what
// the service commits or rolls back, and a test-managed transaction would hide it.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({LinkCodePersistenceAdapter.class, ChannelIdentityPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Tag("persistence")
class LinkChannelIdentityServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant EXPIRY = NOW.plus(Duration.ofMinutes(10));

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private LinkCodePersistenceAdapter linkCodes;
    @Autowired
    private ChannelIdentityPersistenceAdapter identities;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void createTheUser() {
        userId = newUser();
    }

    private LinkChannelIdentityService serviceAt(Instant now) {
        return new LinkChannelIdentityService(linkCodes, identities, Clock.fixed(now, ZoneOffset.UTC),
                transactionManager);
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profile (id, email) VALUES (?, ?)", id, id + "@example.com");
        return id;
    }

    /** Stores a fresh code for the user and returns it as the user would type it. */
    private String issueCodeFor(UUID owner) {
        String code = LinkCodeFormat.generate(new SecureRandom());
        linkCodes.save(LinkCode.create(owner, LinkCodeFormat.hash(code), EXPIRY));
        return code;
    }

    private boolean isUsed(String code) {
        return jdbc.queryForObject("SELECT used_at IS NOT NULL FROM link_code WHERE code_hash = ?",
                Boolean.class, LinkCodeFormat.hash(code));
    }

    private int identitiesOf(UUID owner) {
        return jdbc.queryForObject("SELECT count(*) FROM channel_identity WHERE user_id = ?", Integer.class, owner);
    }

    private void linkedBefore(UUID owner, String externalId) {
        jdbc.update("INSERT INTO channel_identity (id, user_id, channel, external_id) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), owner, Channel.TELEGRAM.name(), externalId);
    }

    @Test
    void linksTheIdentityToTheUserWhoRequestedTheCode() {
        String code = issueCodeFor(userId);

        LinkResult result = serviceAt(NOW).execute(Channel.TELEGRAM, "100", code);

        assertThat(result).isEqualTo(LinkResult.LINKED);
        assertThat(jdbc.queryForObject(
                "SELECT user_id FROM channel_identity WHERE channel = 'TELEGRAM' AND external_id = '100'",
                UUID.class)).isEqualTo(userId);
        assertThat(isUsed(code)).isTrue();
    }

    @Test
    void acceptsTheCodeTypedInLowerCaseAndWithoutTheDash() {
        String code = issueCodeFor(userId);
        String typed = " " + code.replace("-", "").toLowerCase() + " ";

        assertThat(serviceAt(NOW).execute(Channel.TELEGRAM, "101", typed)).isEqualTo(LinkResult.LINKED);
    }

    @Test
    void rejectsACodeNobodyIssued() {
        String neverIssued = LinkCodeFormat.generate(new SecureRandom());

        assertThat(serviceAt(NOW).execute(Channel.TELEGRAM, "102", neverIssued)).isEqualTo(LinkResult.INVALID_CODE);
    }

    @Test
    void rejectsAnExpiredCodeAndLinksNothing() {
        String code = issueCodeFor(userId);

        LinkResult result = serviceAt(EXPIRY).execute(Channel.TELEGRAM, "103", code);

        assertThat(result).isEqualTo(LinkResult.INVALID_CODE);
        assertThat(identitiesOf(userId)).isZero();
    }

    @Test
    void rejectsACodeThatWasAlreadyUsed() {
        String code = issueCodeFor(userId);
        serviceAt(NOW).execute(Channel.TELEGRAM, "104", code);

        LinkResult second = serviceAt(NOW).execute(Channel.TELEGRAM, "105", code);

        assertThat(second).isEqualTo(LinkResult.INVALID_CODE);
        assertThat(identitiesOf(userId)).isEqualTo(1);
    }

    @Test
    void doesNotBurnTheCodeWhenTheChannelAccountBelongsToSomeoneElse() {
        linkedBefore(newUser(), "106");
        String code = issueCodeFor(userId);

        LinkResult result = serviceAt(NOW).execute(Channel.TELEGRAM, "106", code);

        assertThat(result).isEqualTo(LinkResult.CHANNEL_ALREADY_LINKED);
        assertThat(isUsed(code)).isFalse();
        // The code still works from a free account.
        assertThat(serviceAt(NOW).execute(Channel.TELEGRAM, "107", code)).isEqualTo(LinkResult.LINKED);
    }

    @Test
    void doesNotBurnTheCodeWhenTheUserAlreadyLinkedThatChannel() {
        linkedBefore(userId, "108");
        String code = issueCodeFor(userId);

        LinkResult result = serviceAt(NOW).execute(Channel.TELEGRAM, "109", code);

        assertThat(result).isEqualTo(LinkResult.CHANNEL_ALREADY_LINKED);
        assertThat(isUsed(code)).isFalse();
        assertThat(identitiesOf(userId)).isEqualTo(1);
    }

    @Test
    void tellsTheUserWhenTheAccountIsAlreadyLinkedToThem() {
        linkedBefore(userId, "112");
        String code = issueCodeFor(userId);

        LinkResult result = serviceAt(NOW).execute(Channel.TELEGRAM, "112", code);

        assertThat(result).isEqualTo(LinkResult.ALREADY_LINKED_TO_SAME_USER);
        assertThat(isUsed(code)).isFalse();
        assertThat(identitiesOf(userId)).isEqualTo(1);
    }

    @Test
    void leavesAnOrdinaryMessageAloneAndTouchesNothing() {
        String code = issueCodeFor(userId);

        LinkResult result = serviceAt(NOW).execute(Channel.TELEGRAM, "110", "gasté 500 en el super");

        assertThat(result).isEqualTo(LinkResult.NOT_A_CODE);
        assertThat(isUsed(code)).isFalse();
    }

    @Test
    void treatsAMessageWithoutTextAsNotACode() {
        assertThat(serviceAt(NOW).execute(Channel.TELEGRAM, "111", null)).isEqualTo(LinkResult.NOT_A_CODE);
    }
}
