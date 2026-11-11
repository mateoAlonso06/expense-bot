package com.expensebot.accounts.application;

import com.expensebot.accounts.domain.ChannelIdentity;

public interface ChannelIdentityRepository {
    /**
     * Stores the identity unless it conflicts with an existing one: the same channel account already belongs
     * to someone, or the user already linked an account on that channel.
     *
     * @return true if it was stored, false if it conflicted
     */
    boolean saveIfAvailable(ChannelIdentity identity);
}
