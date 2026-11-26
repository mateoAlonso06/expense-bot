package com.expensebot.conversation.infrastructure.llm;

import com.expensebot.conversation.infrastructure.llm.IntentClassifier.Intent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The model is a fake that answers a fixed text: these tests check the mapping and what is sent, not the model. */
@Tag("mocks")
class IntentClassifierTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final IntentClassifier classifier;

    IntentClassifierTest() {
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        classifier = new IntentClassifier(ChatClient.builder(chatModel));
    }

    private void modelAnswers(String text) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    @Test
    void readsANewExpense() {
        modelAnswers("{\"intent\": \"EXPENSE\"}");

        assertThat(classifier.classify("uber 3000 no, 3500")).contains(Intent.EXPENSE);
    }

    @Test
    void readsACorrection() {
        modelAnswers("{\"intent\": \"CORRECTION\"}");

        assertThat(classifier.classify("no, eran 15500")).contains(Intent.CORRECTION);
    }

    @Test
    void reportsNothingWhenTheModelDoesNotAnswerWithJson() {
        modelAnswers("Es un gasto.");

        assertThat(classifier.classify("super 15000")).isEmpty();
    }

    @Test
    void reportsNothingWhenTheAnswerHasNoIntent() {
        modelAnswers("{}");

        assertThat(classifier.classify("super 15000")).isEmpty();
    }

    @Test
    void doesNotHideAProviderFailure() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("401 from the provider"));

        assertThatThrownBy(() -> classifier.classify("super 15000")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sendsTheInstructionsAndOnlyTheMessage() {
        modelAnswers("{\"intent\": \"EXPENSE\"}");

        classifier.classify("gasté {500} en {algo}");

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        String sent = prompt.getValue().getInstructions().stream()
                .map(message -> message.getMessageType() + ": " + message.getText())
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(sent).contains("SYSTEM", "EXPENSE: the message reports a new expense",
                "\"uber 3000 no, 3500\" -> EXPENSE", "\"la nafta eran 3500\" -> CORRECTION");
        assertThat(sent).contains("USER", "Message: gasté {500} en {algo}").doesNotContain("Categories:");
    }
}
