package com.expensebot;

import com.expensebot.conversation.infrastructure.llm.IntentClassifier;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real {@code ChatClient} call, answered by a tiny local HTTP server that plays OpenAI, is measured by Spring AI in
 * latency and tokens. The assertions read the Prometheus scrape, the same text {@code /actuator/prometheus} renders.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
// Spring Boot tests keep metrics in memory only; this turns the Prometheus registry on.
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
@Tag("flow")
class LlmMetricsFlowTest {
    private static final HttpServer FAKE_OPENAI = startFakeOpenAi();

    @Autowired
    private IntentClassifier intentClassifier;
    @Autowired
    private PrometheusMeterRegistry prometheus;

    @DynamicPropertySource
    static void openAiProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url",
                () -> "http://localhost:" + FAKE_OPENAI.getAddress().getPort() + "/v1");
    }

    @AfterAll
    static void stopFakeOpenAi() {
        FAKE_OPENAI.stop(0);
    }

    @Test
    void aModelCallIsMeasuredInLatencyAndTokens() {
        assertThat(intentClassifier.classify("gasté 500 en el super")).hasValue(IntentClassifier.Intent.EXPENSE);

        String scrape = prometheus.scrape();
        // latency: one call, no error
        assertThat(metric(scrape, "gen_ai_client_operation_seconds_count")).contains("error=\"none\"").endsWith(" 1");
        // tokens: the usage OpenAI reported in its answer
        assertThat(scrape).contains("gen_ai_token_type=\"input\"} 120.0", "gen_ai_token_type=\"output\"} 8.0");
    }

    /** The scrape line of a metric (name, tags and value), e.g. {@code name{tag="value"} 1}. */
    private static String metric(String scrape, String name) {
        return scrape.lines().filter(line -> line.startsWith(name + "{")).findFirst().orElseThrow();
    }

    private static HttpServer startFakeOpenAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] response = """
                        {"id": "chatcmpl-1", "object": "chat.completion", "created": 1764115200, "model": "gpt-4o-mini",
                         "choices": [{"index": 0, "finish_reason": "stop", "logprobs": null,
                                      "message": {"role": "assistant", "content": "{\\"intent\\": \\"EXPENSE\\"}",
                                                  "refusal": null}}],
                         "usage": {"prompt_tokens": 120, "completion_tokens": 8, "total_tokens": 128}}"""
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
