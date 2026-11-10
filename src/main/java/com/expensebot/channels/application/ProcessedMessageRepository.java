package com.expensebot.channels.application;

import com.expensebot.conversation.Channel;

public interface ProcessedMessageRepository {

    /**
     * Records that the message was received. Returns true only for the first registration of this
     * {@code (channel, eventId)}. Must be atomic so that concurrent calls for the same message
     * produce exactly one true.
     */
    boolean registerIfNew(Channel channel, String eventId);
}
