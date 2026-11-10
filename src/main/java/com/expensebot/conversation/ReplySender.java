package com.expensebot.conversation;

import com.expensebot.accounts.Channel;

/** Outbound port: sends a text reply to a user on one channel. Each channel adapter implements it. */
public interface ReplySender {

    /** The channel this sender delivers to. */
    Channel channel();

    void send(String externalUserId, String text);
}
