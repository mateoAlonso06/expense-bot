package com.expensebot.accounts;

/**
 * Handles a message that may be a link code: when it is one and it is valid, links the sender's channel identity
 * to the user who requested the code.
 */
public interface LinkChannelIdentityUseCase {

    /**
     * @param channel    where the message came from
     * @param externalId the sender's id on that channel
     * @param text       the message text, exactly as received
     */
    LinkResult execute(Channel channel, String externalId, String text);
}
