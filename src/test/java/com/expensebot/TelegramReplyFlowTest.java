package com.expensebot;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * First flow test: an update posted to the webhook goes through the real controller, mapper, conversation service,
 * reply service, Telegram sender and HTTP client. Only Telegram itself is replaced, by a tiny local HTTP server.
 */
@SpringBootTest(properties = {
        "telegram.bot-token=TEST-TOKEN",
        "telegram.webhook.secret-token=flow-secret"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TelegramReplyFlowTest {
    // Each post needs its own update id: the database keeps the ones already seen, and a repeated
    // id would be dropped as a duplicate.
    private static final AtomicLong UPDATE_IDS = new AtomicLong(System.nanoTime());

    private static final BlockingQueue<CapturedRequest> REQUESTS = new LinkedBlockingQueue<>();
    private static final AtomicInteger TELEGRAM_STATUS = new AtomicInteger(200);
    private static final HttpServer FAKE_TELEGRAM = startFakeTelegram();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void telegramProperties(DynamicPropertyRegistry registry) {
        registry.add("telegram.api-base-url", () -> "http://localhost:" + FAKE_TELEGRAM.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeTelegram() {
        FAKE_TELEGRAM.stop(0);
    }

    @BeforeEach
    void reset() {
        REQUESTS.clear();
        TELEGRAM_STATUS.set(200);
    }

    @Test
    void anUpdateReceivedByTheWebhookIsAnsweredWithRecibido() throws Exception {
        postUpdate();

        CapturedRequest request = REQUESTS.poll(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.path()).isEqualTo("/botTEST-TOKEN/sendMessage");
        JsonNode body = new JsonMapper().readTree(request.body());
        assertThat(body.get("chat_id").longValue()).isEqualTo(42L);
        assertThat(body.get("text").asString()).isEqualTo("Recibido");
    }

    @Test
    void theWebhookStillAnswersOkWhenTelegramRejectsTheReply() throws Exception {
        TELEGRAM_STATUS.set(500);

        postUpdate();

        assertThat(REQUESTS.poll(2, TimeUnit.SECONDS)).isNotNull();
    }

    private void postUpdate() throws Exception {
        mockMvc.perform(post("/api/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "flow-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"update_id": %d, "message": {"message_id": 1, "from": {"id": 42}, "text": "hola"}}"""
                                .formatted(UPDATE_IDS.incrementAndGet())))
                .andExpect(status().isOk());
    }

    private static HttpServer startFakeTelegram() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                REQUESTS.add(new CapturedRequest(exchange.getRequestURI().getPath(), body));
                int status = TELEGRAM_STATUS.get();
                if (status == 200) {
                    byte[] response = "{\"ok\": true, \"result\": {\"message_id\": 1}}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                } else {
                    exchange.sendResponseHeaders(status, -1);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record CapturedRequest(String path, String body) {
    }
}
