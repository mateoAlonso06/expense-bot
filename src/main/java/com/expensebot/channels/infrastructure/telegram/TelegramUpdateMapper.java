package com.expensebot.channels.infrastructure.telegram;

import com.expensebot.accounts.Channel;
import com.expensebot.conversation.IncomingMessage;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Translates a Telegram update into the channel-agnostic {@link IncomingMessage}. */
@Component
public class TelegramUpdateMapper {

    /**
     * Empty when the update is not a new message from a user (edits, channel posts, no sender...). A message without
     * text (a photo without caption, a sticker, a voice note) is still mapped, with a null text, so the user gets an
     * answer instead of silence.
     */
    public Optional<IncomingMessage> toIncomingMessage(TelegramUpdate update) {
        var message = update.message();
        if (message == null || message.from() == null) {
            return Optional.empty();
        }
        // A photo's text travels in `caption`, not in `text`.
        var text = message.text() != null ? message.text() : message.caption();
        return Optional.of(new IncomingMessage(
                Channel.TELEGRAM,
                String.valueOf(message.from().id()),
                String.valueOf(update.updateId()),
                text,
                null));
    }
}
