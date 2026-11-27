package com.expensebot.conversation.infrastructure.llm;

import org.springframework.ai.chat.client.ChatClient;

import java.util.function.Consumer;

/**
 * What an LLM call is for. Each call puts its use case in the {@code ChatClient} request, and
 * {@link LlmUseCaseObservationFilter} copies it as the {@value #TAG} tag onto the model's latency and token metrics.
 */
enum LlmUseCase {
    CLASSIFY_INTENT("classify-intent"),
    READ_EXPENSE("read-expense"),
    READ_CORRECTION("read-correction");

    /** Tag name on the metrics, and the key of the value in the {@code ChatClient} request context. */
    static final String TAG = "expensebot.use_case";

    private final String tagValue;

    LlmUseCase(String tagValue) {
        this.tagValue = tagValue;
    }

    String tagValue() {
        return tagValue;
    }

    /** For {@code chatClient.prompt().advisors(...)}: stores this use case in the request context. */
    Consumer<ChatClient.AdvisorSpec> inRequest() {
        return advisors -> advisors.param(TAG, tagValue);
    }
}
