package com.expensebot.channels.infrastructure.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Only the parts of a Telegram update the bot uses; everything else is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") long updateId,
        TelegramMessage message) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramMessage(
            @JsonProperty("message_id") long messageId,
            TelegramUser from,
            String text,
            String caption) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramUser(long id) {
    }
}
