package com.expensebot.channels.infrastructure.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramApiTest {
    private static final String BASE = "https://api.telegram.org/botSECRET-TOKEN";

    private final RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final TelegramApi api = new TelegramApi(builder.build());

    @Test
    void asksForUpdatesAfterTheOffsetAndParsesThem() {
        server.expect(requestTo(BASE + "/getUpdates?offset=10&timeout=30"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"ok": true, "result": [
                          {"update_id": 10, "message": {"message_id": 1, "from": {"id": 42}, "text": "hola"}}]}""",
                        MediaType.APPLICATION_JSON));

        var updates = api.getUpdates(10, 30);

        assertThat(updates).hasSize(1);
        assertThat(updates.getFirst().updateId()).isEqualTo(10);
        assertThat(updates.getFirst().message().text()).isEqualTo("hola");
        server.verify();
    }

    @Test
    void returnsAnEmptyListWhenNothingArrivedBeforeTheTimeout() {
        server.expect(requestTo(BASE + "/getUpdates?offset=0&timeout=30"))
                .andRespond(withSuccess("{\"ok\": true, \"result\": []}", MediaType.APPLICATION_JSON));

        assertThat(api.getUpdates(0, 30)).isEmpty();
    }

    @Test
    void failsWhenTelegramAnswersOkFalse() {
        server.expect(requestTo(BASE + "/getUpdates?offset=0&timeout=30"))
                .andRespond(withSuccess("{\"ok\": false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> api.getUpdates(0, 30)).isInstanceOf(TelegramApiException.class);
    }

    @Test
    void registersTheWebhookWithItsSecretToken() {
        server.expect(requestTo(BASE + "/setWebhook"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.url").value("https://bot.example.com/api/telegram/webhook"))
                .andExpect(jsonPath("$.secret_token").value("shared-secret"))
                .andRespond(withSuccess("{\"ok\": true, \"result\": true}", MediaType.APPLICATION_JSON));

        api.setWebhook("https://bot.example.com/api/telegram/webhook", "shared-secret");

        server.verify();
    }

    @Test
    void removesTheWebhook() {
        server.expect(requestTo(BASE + "/deleteWebhook"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"ok\": true, \"result\": true}", MediaType.APPLICATION_JSON));

        api.deleteWebhook();

        server.verify();
    }

    @Test
    void failsWhenTelegramRejectsTheWebhook() {
        server.expect(requestTo(BASE + "/setWebhook"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> api.setWebhook("http://insecure.example.com", "shared-secret"))
                .isInstanceOf(TelegramApiException.class)
                .hasMessageContaining("setWebhook")
                .hasMessageContaining("400")
                .hasMessageNotContaining("SECRET-TOKEN")
                .hasMessageNotContaining("shared-secret");
    }

    @Test
    void doesNotLeakTheBotTokenWhenTheCallFails() {
        server.expect(requestTo(BASE + "/getUpdates?offset=0&timeout=30"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> api.getUpdates(0, 30))
                .isInstanceOf(TelegramApiException.class)
                .hasMessageContaining("401")
                .hasMessageNotContaining("SECRET-TOKEN");
    }
}
