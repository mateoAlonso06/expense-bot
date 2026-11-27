package com.expensebot;

import com.expensebot.conversation.application.Extraction;
import com.expensebot.conversation.infrastructure.llm.SpringAiExpenseExtractor;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real {@code ChatClient} calls, answered by a tiny local HTTP server that plays OpenAI, are measured by Spring AI in
 * latency and tokens, tagged with the use case of each call. The assertions read the Prometheus scrape, the same text
 * {@code /actuator/prometheus} renders.
 */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key")
// Spring Boot tests keep metrics in memory only; this turns the Prometheus registry on.
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
@Tag("flow")
class LlmMetricsFlowTest {
    private static final HttpServer FAKE_OPENAI = startFakeOpenAi();

    @Autowired
    private SpringAiExpenseExtractor extractor;
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
    void eachModelCallIsMeasuredInLatencyAndTokensUnderItsUseCase() {
        // Two model calls: classify the intent, then read the expense.
        assertThat(extractor.extract("nafta 5000", List.of("Transporte", "Otros")))
                .isInstanceOf(Extraction.Found.class);

        String scrape = prometheus.scrape();
        for (String useCase : List.of("classify-intent", "read-expense")) {
            String tag = "expensebot_use_case=\"" + useCase + "\"";
            // latency: one call, no error
            assertThat(metric(scrape, "gen_ai_client_operation_seconds_count", tag)).contains("error=\"none\"")
                    .endsWith(" 1");
            // tokens: the usage OpenAI reported in its answer
            assertThat(metric(scrape, "gen_ai_client_token_usage_total", tag, "gen_ai_token_type=\"input\""))
                    .endsWith(" 120.0");
            assertThat(metric(scrape, "gen_ai_client_token_usage_total", tag, "gen_ai_token_type=\"output\""))
                    .endsWith(" 8.0");
        }
    }

    /** The scrape line of a metric with the given tags (name, tags and value), e.g. {@code name{tag="value"} 1}. */
    private static String metric(String scrape, String name, String... tags) {
        return scrape.lines()
                .filter(line -> line.startsWith(name + "{"))
                .filter(line -> Arrays.stream(tags).allMatch(line::contains))
                .findFirst().orElseThrow(() -> new AssertionError("No " + name + " with " + Arrays.toString(tags)));
    }

    private static HttpServer startFakeOpenAi() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                // The intent classifier's system prompt starts with "You classify"; any other call reads the expense.
                String content = request.contains("You classify")
                        ? """
                          {"intent": "EXPENSE"}"""
                        : """
                          {"amount": 5000, "amountCandidates": [], "description": "nafta", "category": "Transporte"}""";
                byte[] response = """
                        {"id": "chatcmpl-1", "object": "chat.completion", "created": 1764115200, "model": "gpt-4o-mini",
                         "choices": [{"index": 0, "finish_reason": "stop", "logprobs": null,
                                      "message": {"role": "assistant", "content": "%s", "refusal": null}}],
                         "usage": {"prompt_tokens": 120, "completion_tokens": 8, "total_tokens": 128}}"""
                        // the answer is a JSON string inside the JSON response, so its quotes are escaped
                        .formatted(content.replace("\"", "\\\"")).getBytes(StandardCharsets.UTF_8);
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
