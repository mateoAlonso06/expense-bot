package com.expensebot.accounts.application;

import com.expensebot.accounts.Channel;
import com.expensebot.accounts.domain.ChannelIdentity;

import java.util.Optional;
import java.util.UUID;

public interface ChannelIdentityRepository {
    /**
     * Stores the identity unless it conflicts with an existing one: the same channel account already belongs
     * to someone, or the user already linked an account on that channel.
     *
     * @return true if it was stored, false if it conflicted
     */
    boolean saveIfAvailable(ChannelIdentity identity);

    /** The user who linked this channel account, or empty if nobody did. */
    Optional<UUID> findUserId(Channel channel, String externalId);
}
