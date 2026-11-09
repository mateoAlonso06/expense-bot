package com.expensebot.channels.infrastructure.telegram;

import com.expensebot.conversation.Channel;
import com.expensebot.conversation.ReplySender;
import org.springframework.stereotype.Component;

/**
 * Delivers replies through Telegram. It is active in both modes: replying does not depend on how updates arrive.
 * In a private chat the chat id is assumed to be the user's id (not stated by Telegram's docs, to confirm live).
 */
@Component
public class TelegramReplySender implements ReplySender {
    private final TelegramApi telegramApi;

    public TelegramReplySender(TelegramApi telegramApi) {
        this.telegramApi = telegramApi;
    }

    @Override
    public Channel channel() {
        return Channel.TELEGRAM;
    }

    @Override
    public void send(String externalUserId, String text) {
        telegramApi.sendMessage(Long.parseLong(externalUserId), text);
    }
}
