package com.expensebot.channels.infrastructure.telegram.web;

import com.expensebot.channels.infrastructure.telegram.TelegramApi;
import com.expensebot.channels.infrastructure.telegram.TelegramApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookRegistrarTest {
    @Mock
    private TelegramApi telegramApi;

    @Test
    void registersTheWebhookAtTheEndpointPath() {
        new TelegramWebhookRegistrar(telegramApi, "https://bot.example.com", "shared-secret").run(null);

        verify(telegramApi).setWebhook("https://bot.example.com/api/telegram/webhook", "shared-secret");
    }

    @Test
    void ignoresTrailingSlashesInThePublicUrl() {
        new TelegramWebhookRegistrar(telegramApi, "https://bot.example.com/", "shared-secret").run(null);

        verify(telegramApi).setWebhook("https://bot.example.com/api/telegram/webhook", "shared-secret");
    }

    @Test
    void doesNothingWithoutAPublicUrl() {
        new TelegramWebhookRegistrar(telegramApi, "", "shared-secret").run(null);

        verifyNoInteractions(telegramApi);
    }

    @Test
    void doesNothingWithoutASecret() {
        new TelegramWebhookRegistrar(telegramApi, "https://bot.example.com", "").run(null);

        verifyNoInteractions(telegramApi);
    }

    @Test
    void doesNotFailTheStartupWhenTelegramRejectsTheWebhook() {
        doThrow(new TelegramApiException("setWebhook answered HTTP 400"))
                .when(telegramApi).setWebhook(anyString(), anyString());
        var registrar = new TelegramWebhookRegistrar(telegramApi, "https://bot.example.com", "shared-secret");

        assertThatCode(() -> registrar.run(null)).doesNotThrowAnyException();
    }
}
