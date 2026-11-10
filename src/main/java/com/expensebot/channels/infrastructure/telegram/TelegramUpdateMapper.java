package com.expensebot.channels.infrastructure.telegram;

import com.expensebot.accounts.Channel;
import com.expensebot.conversation.IncomingMessage;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Translates a Telegram update into the channel-agnostic {@link IncomingMessage}. */
@Component
public class TelegramUpdateMapper {

    /** Empty when the update is not a message the bot can act on (edits, stickers, no sender...). */
    public Optional<IncomingMessage> toIncomingMessage(TelegramUpdate update) {
        var message = update.message();
        if (message == null || message.from() == null) {
            return Optional.empty();
        }
        // A photo's text travels in `caption`, not in `text`.
        var text = message.text() != null ? message.text() : message.caption();
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new IncomingMessage(
                Channel.TELEGRAM,
                String.valueOf(message.from().id()),
                String.valueOf(update.updateId()),
                text,
                null));
    }
}
