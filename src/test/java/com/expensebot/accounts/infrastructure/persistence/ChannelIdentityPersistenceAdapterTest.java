package com.expensebot.accounts.infrastructure.persistence;

import com.expensebot.accounts.Channel;
import com.expensebot.accounts.domain.ChannelIdentity;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ChannelIdentityPersistenceAdapter.class)
@Tag("persistence")
class ChannelIdentityPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private ChannelIdentityPersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void storesANewIdentity() {
        var userId = newUser();
        var identity = ChannelIdentity.create(userId, Channel.TELEGRAM, "42");

        assertThat(adapter.saveIfAvailable(identity)).isTrue();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM channel_identity WHERE id = ?", identity.getId());
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("channel")).isEqualTo("TELEGRAM");
        assertThat(row.get("external_id")).isEqualTo("42");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void rejectsAnAccountThatAlreadyBelongsToAnotherUser() {
        var externalId = newExternalId();
        var owner = newUser();
        adapter.saveIfAvailable(ChannelIdentity.create(owner, Channel.TELEGRAM, externalId));

        assertThat(adapter.saveIfAvailable(ChannelIdentity.create(newUser(), Channel.TELEGRAM, externalId))).isFalse();

        assertThat(ownerOf(Channel.TELEGRAM, externalId)).isEqualTo(owner);
    }

    @Test
    void rejectsASecondAccountOnTheSameChannelForTheSameUser() {
        var userId = newUser();
        adapter.saveIfAvailable(ChannelIdentity.create(userId, Channel.TELEGRAM, newExternalId()));

        assertThat(adapter.saveIfAvailable(ChannelIdentity.create(userId, Channel.TELEGRAM, newExternalId()))).isFalse();
    }

    @Test
    void acceptsTheSameUserOnAnotherChannel() {
        var userId = newUser();
        adapter.saveIfAvailable(ChannelIdentity.create(userId, Channel.TELEGRAM, newExternalId()));

        assertThat(adapter.saveIfAvailable(ChannelIdentity.create(userId, Channel.WHATSAPP, newExternalId()))).isTrue();
    }

    @Test
    void acceptsTheSameExternalIdOnAnotherChannel() {
        var externalId = newExternalId();
        adapter.saveIfAvailable(ChannelIdentity.create(newUser(), Channel.TELEGRAM, externalId));

        assertThat(adapter.saveIfAvailable(ChannelIdentity.create(newUser(), Channel.WHATSAPP, externalId))).isTrue();
    }

    @Test
    void failsForAUserThatDoesNotExist() {
        var identity = ChannelIdentity.create(UUID.randomUUID(), Channel.TELEGRAM, newExternalId());

        assertThatThrownBy(() -> adapter.saveIfAvailable(identity)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsTheUserWhoLinkedTheAccount() {
        var userId = newUser();
        var externalId = newExternalId();
        adapter.saveIfAvailable(ChannelIdentity.create(userId, Channel.TELEGRAM, externalId));

        assertThat(adapter.findUserId(Channel.TELEGRAM, externalId)).contains(userId);
    }

    @Test
    void findsNobodyForAnAccountThatWasNeverLinked() {
        assertThat(adapter.findUserId(Channel.TELEGRAM, newExternalId())).isEmpty();
    }

    @Test
    void doesNotMixUpTheSameExternalIdOnAnotherChannel() {
        var externalId = newExternalId();
        adapter.saveIfAvailable(ChannelIdentity.create(newUser(), Channel.TELEGRAM, externalId));

        assertThat(adapter.findUserId(Channel.WHATSAPP, externalId)).isEmpty();
    }

    private UUID newUser() {
        var userId = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profile (id, email) VALUES (?, ?)", userId, userId + "@example.com");
        return userId;
    }

    private static String newExternalId() {
        return UUID.randomUUID().toString();
    }

    private UUID ownerOf(Channel channel, String externalId) {
        return jdbc.queryForObject("SELECT user_id FROM channel_identity WHERE channel = ? AND external_id = ?",
                UUID.class, channel.name(), externalId);
    }
}
