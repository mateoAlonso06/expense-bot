package com.expensebot.conversation.infrastructure.llm;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmUseCaseObservationFilterTest {

    private final ObservationRegistry registry = ObservationRegistry.create();
    private final LlmUseCaseObservationFilter filter = new LlmUseCaseObservationFilter();

    LlmUseCaseObservationFilterTest() {
        // Without a handler the registry hands out no-op observations, which keep no parent.
        registry.observationConfig().observationHandler(context -> true);
    }

    @Test
    void tagsTheModelCallWithTheUseCaseOfTheChatClientCallAboveIt() {
        Observation chatClientCall = chatClientCall(Map.of(LlmUseCase.TAG, "read-correction"));
        // As in Spring AI, advisors sit between the ChatClient call and the model call.
        Observation advisor = child(new Observation.Context(), chatClientCall);
        ChatModelObservationContext modelCall = modelCall();
        child(modelCall, advisor);

        filter.map(modelCall);

        assertThat(useCaseTag(modelCall)).isEqualTo("read-correction");
    }

    @Test
    void tagsAModelCallWithoutAUseCaseAsUnknown() {
        ChatModelObservationContext modelCall = modelCall();
        child(modelCall, chatClientCall(Map.of()));

        filter.map(modelCall);

        assertThat(useCaseTag(modelCall)).isEqualTo("unknown");
    }

    @Test
    void tagsAModelCallOutsideAChatClientCallAsUnknown() {
        ChatModelObservationContext modelCall = modelCall();
        Observation.start("gen_ai.client.operation", () -> modelCall, registry);

        filter.map(modelCall);

        assertThat(useCaseTag(modelCall)).isEqualTo("unknown");
    }

    @Test
    void leavesOtherObservationsUntouched() {
        Observation.Context chatClientContext = chatClientCall(Map.of(LlmUseCase.TAG, "read-expense")).getContext();

        filter.map(chatClientContext);

        assertThat(chatClientContext.getLowCardinalityKeyValue(LlmUseCase.TAG)).isNull();
    }

    private Observation chatClientCall(Map<String, Object> requestContext) {
        ChatClientRequest request = ChatClientRequest.builder().prompt(new Prompt("hola")).context(requestContext).build();
        return Observation.start("spring.ai.chat.client",
                () -> ChatClientObservationContext.builder().request(request).build(), registry);
    }

    private Observation child(Observation.Context context, Observation parent) {
        return Observation.createNotStarted("child", () -> context, registry).parentObservation(parent).start();
    }

    private static ChatModelObservationContext modelCall() {
        return ChatModelObservationContext.builder().prompt(new Prompt("hola")).provider("openai").build();
    }

    private static String useCaseTag(Observation.Context context) {
        return context.getLowCardinalityKeyValue(LlmUseCase.TAG).getValue();
    }
}
