package com.expensebot;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Makes ONE real call to OpenAI to prove the configuration (key, model) works end to end. It costs a few tokens, so
 * it never runs by itself: neither in the CI nor in a plain {@code ./mvnw test}. To run it:
 * <pre>
 * export OPENAI_API_KEY=...    # the real key
 * RUN_LLM_TESTS=true ./mvnw test -Dtest=OpenAiConnectionTest
 * </pre>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
class OpenAiConnectionTest {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    void theModelAnswersAShortPrompt() {
        String answer = chatClientBuilder.build()
                .prompt()
                .user("Reply with exactly one word: pong")
                .call()
                .content();

        assertThat(answer).isNotBlank();
    }
}
