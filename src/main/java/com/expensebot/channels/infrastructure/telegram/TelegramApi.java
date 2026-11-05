package com.expensebot.channels.infrastructure.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/** Calls to the Telegram Bot API. The bot token travels in the URL, so it must never reach a log. */
@Component
public class TelegramApi {
    private final RestClient restClient;

    @Autowired
    public TelegramApi(@Value("${telegram.api-base-url:https://api.telegram.org}") String baseUrl,
                       @Value("${telegram.bot-token:}") String botToken) {
        this(RestClient.builder().baseUrl(baseUrl + "/bot" + botToken).build());
    }

    TelegramApi(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Long polling: Telegram holds the request open up to {@code timeoutSeconds} until an update arrives.
     * {@code offset} must be the highest update id already handled plus one, which also confirms the earlier ones.
     */
    public List<TelegramUpdate> getUpdates(long offset, int timeoutSeconds) {
        TelegramResponse<List<TelegramUpdate>> response;
        try {
            response = restClient.get()
                    .uri("/getUpdates?offset={offset}&timeout={timeout}", offset, timeoutSeconds)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientResponseException e) {
            throw new TelegramApiException("getUpdates answered HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            // The original message carries the request URL, and with it the bot token: keep only the type.
            throw new TelegramApiException("getUpdates failed: " + e.getClass().getSimpleName());
        }
        if (response == null || !response.ok() || response.result() == null) {
            throw new TelegramApiException("getUpdates answered with ok=false");
        }
        return response.result();
    }
}
