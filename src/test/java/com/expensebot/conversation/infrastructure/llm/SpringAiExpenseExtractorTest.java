package com.expensebot.conversation.infrastructure.llm;

import com.expensebot.conversation.application.ExpenseDraft;
import com.expensebot.conversation.application.Extraction;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The model is a fake that answers a fixed text, so these tests need no API key and never touch the network. The
 * intent classifier is a mock too: its own prompt is tested in {@link IntentClassifierTest}; here it only picks which
 * reading runs.
 */
@Tag("mocks")
class SpringAiExpenseExtractorTest {
    private static final List<String> CATEGORIES = List.of("Alimentación", "Transporte", "Otros");

    private final ChatModel chatModel = mock(ChatModel.class);
    private final IntentClassifier intentClassifier = mock(IntentClassifier.class);
    private final SpringAiExpenseExtractor extractor;

    SpringAiExpenseExtractorTest() {
        // The ChatClient asks the model for its default options before every call; a bare mock would answer null.
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        // Most cases are new expenses; the correction tests say so.
        when(intentClassifier.classify(any())).thenReturn(Optional.of(IntentClassifier.Intent.EXPENSE));
        extractor = new SpringAiExpenseExtractor(ChatClient.builder(chatModel), intentClassifier);
    }

    private void classifiedAsACorrection() {
        when(intentClassifier.classify(any())).thenReturn(Optional.of(IntentClassifier.Intent.CORRECTION));
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

    private ExpenseDraft found(String message) {
        Extraction extraction = extractor.extract(message, CATEGORIES);
        assertThat(extraction).isInstanceOf(Extraction.Found.class);
        return ((Extraction.Found) extraction).draft();
    }

    @Test
    void readsTheExpenseTheModelFoundInTheMessage() {
        modelAnswers("{\"amount\": 15000, \"description\": \"carnicería\", \"category\": \"Alimentación\"}");

        ExpenseDraft expense = found("$15.000 carnicería");

        assertThat(expense.amount()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(expense.description()).isEqualTo("carnicería");
        assertThat(expense.categoryName()).isEqualTo("Alimentación");
    }

    @Test
    void keepsDecimalsOfTheAmount() {
        modelAnswers("{\"amount\": 1234.50, \"description\": null, \"category\": null}");

        assertThat(found("1234,50").amount()).isEqualByComparingTo("1234.50");
    }

    @Test
    void onlyTheAmountIsRequired() {
        modelAnswers("{\"amount\": 500, \"description\": null, \"category\": null}");

        ExpenseDraft expense = found("500");

        assertThat(expense.description()).isNull();
        assertThat(expense.categoryName()).isNull();
    }

    @Test
    void turnsBlankTextFieldsIntoNothing() {
        modelAnswers("{\"amount\": 500, \"description\": \"  \", \"category\": \"\"}");

        ExpenseDraft expense = found("500");

        assertThat(expense.description()).isNull();
        assertThat(expense.categoryName()).isNull();
    }

    @Test
    void reportsAMissingAmountWithWhatWasBought() {
        modelAnswers("{\"amount\": null, \"description\": \"café\", \"category\": \"Alimentación\"}");

        assertThat(extractor.extract("tomé un café", CATEGORIES))
                .isEqualTo(new Extraction.MissingAmount("café", "Alimentación"));
    }

    @Test
    void reportsAMissingAmountWithNothingElseWhenTheMessageSaysNothing() {
        modelAnswers("{\"amount\": null, \"description\": \" \", \"category\": null}");

        assertThat(extractor.extract("hola", CATEGORIES)).isEqualTo(new Extraction.MissingAmount(null, null));
    }

    @Test
    void reportsTheCandidatesWhenTheAmountIsAmbiguous() {
        modelAnswers("{\"amount\": null, \"amountCandidates\": [5000, 3000, 5000.00], "
                + "\"description\": \"súper\", \"category\": \"Alimentación\"}");

        Extraction extraction = extractor.extract("súper 5000 o 3000", CATEGORIES);

        // repeats by value are dropped and the message order is kept
        assertThat(extraction).isInstanceOfSatisfying(Extraction.AmbiguousAmount.class, ambiguous -> {
            assertThat(ambiguous.candidates()).usingElementComparator(BigDecimal::compareTo)
                    .containsExactly(new BigDecimal("5000"), new BigDecimal("3000"));
            assertThat(ambiguous.description()).isEqualTo("súper");
            assertThat(ambiguous.categoryName()).isEqualTo("Alimentación");
        });
    }

    @Test
    void candidatesWinOverASingleAmount() {
        // the model contradicted itself: asking is safer than storing a guess
        modelAnswers("{\"amount\": 5000, \"amountCandidates\": [5000, 3000], \"description\": null, "
                + "\"category\": null}");

        assertThat(extractor.extract("5000 o 3000", CATEGORIES)).isInstanceOf(Extraction.AmbiguousAmount.class);
    }

    @Test
    void oneCandidateIsNotADoubt() {
        modelAnswers("{\"amount\": 6000, \"amountCandidates\": [6000, 6000.00], \"description\": null, "
                + "\"category\": null}");

        assertThat(found("5000, perdón, 6000").amount()).isEqualByComparingTo("6000");
    }

    @Test
    void readsACorrectionWithOnlyTheNewValuesAndTheExpenseItNames() {
        classifiedAsACorrection();
        modelAnswers("{\"amount\": 3500, \"description\": null, \"category\": null, \"reference\": \"la nafta\"}");

        assertThat(extractor.extract("la nafta eran 3500", CATEGORIES))
                .isEqualTo(new Extraction.Correction(new BigDecimal("3500"), null, null, "la nafta"));
    }

    @Test
    void aCorrectionCanChangeNothingEditable() {
        // for example "cambiá la fecha a ayer": the date is not editable, so no field comes back
        classifiedAsACorrection();
        modelAnswers("{\"amount\": null, \"description\": \" \", \"category\": null, \"reference\": \"\"}");

        assertThat(extractor.extract("cambiá la fecha a ayer", CATEGORIES))
                .isEqualTo(new Extraction.Correction(null, null, null, null));
    }

    @Test
    void readsACorrectionWithTheCorrectionPrompt() {
        classifiedAsACorrection();
        modelAnswers("{\"amount\": null, \"description\": null, \"category\": \"Otros\", \"reference\": null}");

        extractor.extract("categoría otros", CATEGORIES);

        String sent = sentToTheModel();
        assertThat(sent).contains("SYSTEM", "corrects an expense the user already recorded", "Argentine number format",
                "Do not infer a category", "never in description");
        assertThat(sent).doesNotContain("amountCandidates lists every one of them");
        assertThat(sent).contains("USER", "Categories: Alimentación, Transporte, Otros", "Message: categoría otros");
        assertThat(sent).contains("how the user names the expense to correct");
    }

    @Test
    void doesNotReadTheMessageWhenTheIntentIsUnreadable() {
        when(intentClassifier.classify(any())).thenReturn(Optional.empty());

        assertThat(extractor.extract("hola", CATEGORIES)).isInstanceOf(Extraction.Unreadable.class);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void reportsAnUnreadableAnswerWhenTheCorrectionIsNotJson() {
        classifiedAsACorrection();
        modelAnswers("No entendí.");

        assertThat(extractor.extract("no, eran 15500", CATEGORIES)).isInstanceOf(Extraction.Unreadable.class);
    }

    @Test
    void reportsAnUnreadableAnswerWhenTheModelDoesNotAnswerWithJson() {
        modelAnswers("Lo siento, no entendí el mensaje.");

        assertThat(extractor.extract("hola", CATEGORIES)).isInstanceOf(Extraction.Unreadable.class);
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
        assertThat(sent).contains("SYSTEM", "Argentine number format", "amountCandidates lists every one of them");
        assertThat(sent).doesNotContain("corrects an expense the user already recorded");
        assertThat(sent).contains("USER", "Categories: Alimentación, Transporte, Otros", "Message: $15.000 carnicería");
        // the field descriptions travel with the schema
        assertThat(sent).contains("Total amount as a plain number", "only when there are two or more");
    }

    @Test
    void aMessageWithCurlyBracesDoesNotBreakThePrompt() {
        modelAnswers("{\"amount\": 500, \"description\": null, \"category\": null}");

        assertThat(extractor.extract("gasté {500} en {algo}", CATEGORIES)).isInstanceOf(Extraction.Found.class);
        assertThat(sentToTheModel()).contains("Message: gasté {500} en {algo}");
    }
}
