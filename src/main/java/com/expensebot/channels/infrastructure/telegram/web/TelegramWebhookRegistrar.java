package com.expensebot.channels.infrastructure.telegram.web;

import com.expensebot.channels.infrastructure.telegram.TelegramApi;
import com.expensebot.channels.infrastructure.telegram.TelegramApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** On startup in webhook mode, tells Telegram where to send updates, so switching modes needs no manual step. */
@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.mode", havingValue = "webhook", matchIfMissing = true)
public class TelegramWebhookRegistrar implements ApplicationRunner {
    private final TelegramApi telegramApi;
    private final String publicUrl;
    private final String secretToken;

    public TelegramWebhookRegistrar(TelegramApi telegramApi,
                                    @Value("${telegram.webhook.url:}") String publicUrl,
                                    @Value("${telegram.webhook.secret-token:}") String secretToken) {
        this.telegramApi = telegramApi;
        this.publicUrl = publicUrl;
        this.secretToken = secretToken;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (publicUrl.isBlank() || secretToken.isBlank()) {
            log.warn("Telegram webhook not registered: set TELEGRAM_WEBHOOK_URL and TELEGRAM_WEBHOOK_SECRET");
            return;
        }
        String url = publicUrl.replaceAll("/+$", "") + TelegramWebhookController.PATH;
        try {
            telegramApi.setWebhook(url, secretToken);
            log.info("Telegram webhook registered at {}", url);
        } catch (TelegramApiException e) {
            // Not fatal: the rest of the application works, and the next restart registers it again.
            log.error("Could not register the Telegram webhook: {}", e.getMessage());
        }
    }
}
