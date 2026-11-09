package com.expensebot.channels.infrastructure.telegram;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
        return call("getUpdates", () -> restClient.get()
                .uri("/getUpdates?offset={offset}&timeout={timeout}", offset, timeoutSeconds)
                .retrieve()
                .body(new ParameterizedTypeReference<TelegramResponse<List<TelegramUpdate>>>() {
                }));
    }

    /** Tells Telegram to push updates to {@code url}; it sends {@code secretToken} in a header on every request. */
    public void setWebhook(String url, String secretToken) {
        call("setWebhook", () -> restClient.post()
                .uri("/setWebhook")
                .body(Map.of("url", url, "secret_token", secretToken))
                .retrieve()
                .body(new ParameterizedTypeReference<TelegramResponse<Boolean>>() {
                }));
    }

    /** Required before long polling: {@code getUpdates} does not work while a webhook is set. */
    public void deleteWebhook() {
        call("deleteWebhook", () -> restClient.post()
                .uri("/deleteWebhook")
                .retrieve()
                .body(new ParameterizedTypeReference<TelegramResponse<Boolean>>() {
                }));
    }

    private <T> T call(String method, Supplier<TelegramResponse<T>> request) {
        TelegramResponse<T> response;
        try {
            response = request.get();
        } catch (RestClientResponseException e) {
            throw new TelegramApiException(method + " answered HTTP " + e.getStatusCode().value());
        } catch (RuntimeException e) {
            // The original message carries the request URL, and with it the bot token: keep only the type.
            throw new TelegramApiException(method + " failed: " + e.getClass().getSimpleName());
        }
        if (response == null || !response.ok() || response.result() == null) {
            throw new TelegramApiException(method + " answered with ok=false");
        }
        return response.result();
    }
}
