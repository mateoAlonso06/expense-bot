package com.expensebot.accounts.domain;

import com.expensebot.accounts.Channel;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

/** The account a user has on a messaging channel, as the channel itself identifies it. */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChannelIdentity {
    @EqualsAndHashCode.Include
    private final UUID id;
    private final UUID userId;
    private final Channel channel;
    private final String externalId;

    private ChannelIdentity(UUID id, UUID userId, Channel channel, String externalId) {
        this.id = id;
        this.userId = userId;
        this.channel = channel;
        this.externalId = externalId;
    }

    /** An identity that has just been linked to a user. */
    public static ChannelIdentity create(UUID userId, Channel channel, String externalId) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(channel, "Channel is required");
        Objects.requireNonNull(externalId, "External id is required");
        if (externalId.isBlank()) {
            throw new IllegalArgumentException("External id must not be blank");
        }
        return new ChannelIdentity(UUID.randomUUID(), userId, channel, externalId);
    }
}
