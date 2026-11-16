package com.expensebot.conversation.infrastructure.llm;

import com.expensebot.conversation.application.ExpenseDraft;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The model is a fake that answers a fixed text, so these tests need no API key and never touch the network. */
@Tag("mocks")
class SpringAiExpenseExtractorTest {
    private static final List<String> CATEGORIES = List.of("Alimentación", "Transporte", "Otros");

    private final ChatModel chatModel = mock(ChatModel.class);
    private final SpringAiExpenseExtractor extractor;

    SpringAiExpenseExtractorTest() {
        // The ChatClient asks the model for its default options before every call; a bare mock would answer null.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        extractor = new SpringAiExpenseExtractor(ChatClient.builder(chatModel));
    }

    private void modelAnswers(String text) {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
    }

    private String sentToTheModel() {
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        return prompt.getValue().getInstructions().stream()
                .map(message -> message.getMessageType() + ": " + message.getText())
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    void readsTheExpenseTheModelFoundInTheMessage() {
        modelAnswers("{\"amount\": 15000, \"description\": \"carnicería\", \"category\": \"Alimentación\"}");

        Optional<ExpenseDraft> draft = extractor.extract("$15.000 carnicería", CATEGORIES);

        assertThat(draft).hasValueSatisfying(expense -> {
            assertThat(expense.amount()).isEqualByComparingTo(new BigDecimal("15000"));
            assertThat(expense.description()).isEqualTo("carnicería");
            assertThat(expense.categoryName()).isEqualTo("Alimentación");
        });
    }

    @Test
    void keepsDecimalsOfTheAmount() {
        modelAnswers("{\"amount\": 1234.50, \"description\": null, \"category\": null}");

        assertThat(extractor.extract("1234,50", CATEGORIES)).hasValueSatisfying(
                expense -> assertThat(expense.amount()).isEqualByComparingTo("1234.50"));
    }

    @Test
    void onlyTheAmountIsRequired() {
        modelAnswers("{\"amount\": 500, \"description\": null, \"category\": null}");

        assertThat(extractor.extract("500", CATEGORIES)).hasValueSatisfying(expense -> {
            assertThat(expense.description()).isNull();
            assertThat(expense.categoryName()).isNull();
        });
    }

    @Test
    void turnsBlankTextFieldsIntoNothing() {
        modelAnswers("{\"amount\": 500, \"description\": \"  \", \"category\": \"\"}");

        assertThat(extractor.extract("500", CATEGORIES)).hasValueSatisfying(expense -> {
            assertThat(expense.description()).isNull();
            assertThat(expense.categoryName()).isNull();
        });
    }

    @Test
    void findsNothingWhenTheModelSaysThereIsNoAmount() {
        modelAnswers("{\"amount\": null, \"description\": \"café\", \"category\": null}");

        assertThat(extractor.extract("tomé un café", CATEGORIES)).isEmpty();
    }

    @Test
    void findsNothingWhenTheModelDoesNotAnswerWithJson() {
        modelAnswers("Lo siento, no entendí el mensaje.");

        assertThat(extractor.extract("hola", CATEGORIES)).isEmpty();
    }

    @Test
    void doesNotHideAProviderFailureAsAnUnreadableMessage() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("401 from the provider"));

        assertThatThrownBy(() -> extractor.extract("$15.000 carnicería", CATEGORIES))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sendsTheInstructionsTheMessageAndTheCategoriesToTheModel() {
        modelAnswers("{\"amount\": 15000, \"description\": null, \"category\": null}");

        extractor.extract("$15.000 carnicería", CATEGORIES);

        String sent = sentToTheModel();
        assertThat(sent).contains("SYSTEM", "Argentine number format");
        assertThat(sent).contains("USER", "Categories: Alimentación, Transporte, Otros", "Message: $15.000 carnicería");
        assertThat(sent).contains("Total amount as a plain number"); // the field descriptions travel with the schema
    }

    @Test
    void aMessageWithCurlyBracesDoesNotBreakThePrompt() {
        modelAnswers("{\"amount\": 500, \"description\": null, \"category\": null}");

        assertThat(extractor.extract("gasté {500} en {algo}", CATEGORIES)).isPresent();
        assertThat(sentToTheModel()).contains("Message: gasté {500} en {algo}");
    }
}
