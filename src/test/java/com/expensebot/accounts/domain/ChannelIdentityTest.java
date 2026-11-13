package com.expensebot.accounts.domain;

import com.expensebot.accounts.Channel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class ChannelIdentityTest {

    @Test
    void createsAnIdentityWithItsOwnId() {
        var userId = UUID.randomUUID();

        var identity = ChannelIdentity.create(userId, Channel.TELEGRAM, "42");

        assertThat(identity.getId()).isNotNull();
        assertThat(identity.getUserId()).isEqualTo(userId);
        assertThat(identity.getChannel()).isEqualTo(Channel.TELEGRAM);
        assertThat(identity.getExternalId()).isEqualTo("42");
    }

    @Test
    void requiresUserChannelAndExternalId() {
        var userId = UUID.randomUUID();

        assertThatThrownBy(() -> ChannelIdentity.create(null, Channel.TELEGRAM, "42"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ChannelIdentity.create(userId, null, "42"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ChannelIdentity.create(userId, Channel.TELEGRAM, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsABlankExternalId() {
        assertThatThrownBy(() -> ChannelIdentity.create(UUID.randomUUID(), Channel.TELEGRAM, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
