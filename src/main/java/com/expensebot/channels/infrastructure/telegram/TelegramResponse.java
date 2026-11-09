package com.expensebot.channels.infrastructure.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Envelope every Telegram Bot API method answers with. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramResponse<T>(boolean ok, T result) {
}
