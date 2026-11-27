package com.expensebot.conversation.infrastructure.llm;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationView;
import org.springframework.ai.chat.client.observation.ChatClientObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.stereotype.Component;

/**
 * Adds the {@value LlmUseCase#TAG} tag to every chat model observation, which is where Spring AI measures latency
 * ({@code gen_ai.client.operation}) and tokens ({@code gen_ai.client.token.usage}). The use case comes from the
 * enclosing {@code ChatClient} call. A filter runs when the observation stops, before the metrics are recorded, so
 * both metrics get the tag.
 */
@Component
class LlmUseCaseObservationFilter implements ObservationFilter {

    /** For a model call made without a use case. Every metric of one name needs the same tag names in Prometheus. */
    static final String UNKNOWN = "unknown";

    @Override
    public Observation.Context map(Observation.Context context) {
        if (context instanceof ChatModelObservationContext) {
            context.addLowCardinalityKeyValue(KeyValue.of(LlmUseCase.TAG, useCaseOf(context)));
        }
        return context;
    }

    /**
     * The model call is not a direct child of the {@code ChatClient} call (advisors sit in between), so this walks up
     * the parents until it finds it.
     */
    private static String useCaseOf(Observation.ContextView context) {
        ObservationView parent = context.getParentObservation();
        while (parent != null) {
            Observation.ContextView parentContext = parent.getContextView();
            if (parentContext instanceof ChatClientObservationContext chatClientCall) {
                // Set by LlmUseCase.inRequest() through advisors.param(...).
                Object useCase = chatClientCall.getRequest().context().get(LlmUseCase.TAG);
                return useCase != null ? useCase.toString() : UNKNOWN;
            }
            parent = parentContext.getParentObservation();
        }
        return UNKNOWN;
    }
}
