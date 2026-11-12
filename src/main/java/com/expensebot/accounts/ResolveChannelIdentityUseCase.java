package com.expensebot.accounts;

import java.util.Optional;
import java.util.UUID;

/**
 * Tells which user a channel account belongs to. This is how a message from the bot is tied to a user: the
 * channel supplies the sender's id, and only a previously linked account resolves to a user.
 */
public interface ResolveChannelIdentityUseCase {

    /** The linked user's id, or empty if that channel account has not been linked. */
    Optional<UUID> execute(Channel channel, String externalId);
}
