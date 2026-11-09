package com.expensebot.channels.infrastructure.telegram;

/** A failed Telegram call. Its message is safe to log: it never contains the request URL. */
public class TelegramApiException extends RuntimeException {
    public TelegramApiException(String message) {
        super(message);
    }
}
