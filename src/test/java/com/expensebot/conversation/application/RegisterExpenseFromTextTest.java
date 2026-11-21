package com.expensebot.conversation.application;

import com.expensebot.accounts.Channel;
import com.expensebot.conversation.Attachment;
import com.expensebot.conversation.AttachmentType;
import com.expensebot.conversation.IncomingMessage;
import com.expensebot.conversation.domain.PendingClarification;
import com.expensebot.expenses.CategoryView;
import com.expensebot.expenses.ListCategoriesUseCase;
import com.expensebot.expenses.RegisterExpenseCommand;
import com.expensebot.expenses.RegisterExpenseUseCase;
import com.expensebot.expenses.RegisteredExpense;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class RegisterExpenseFromTextTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FOOD_ID = UUID.randomUUID();
    private static final List<CategoryView> CATALOG = List.of(
            new CategoryView(FOOD_ID, "Alimentación"),
            new CategoryView(UUID.randomUUID(), "Otros"));
    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock
    private ListCategoriesUseCase listCategories;
    @Mock
    private RegisterExpenseUseCase registerExpense;
    @Mock
    private ExpenseExtractor expenseExtractor;
    @Mock
    private PendingClarificationRepository pendingClarifications;

    private RegisterExpenseFromText registerExpenseFromText;

    @BeforeEach
    void createTheService() {
        registerExpenseFromText = new RegisterExpenseFromText(listCategories, registerExpense, expenseExtractor,
                pendingClarifications, Clock.fixed(NOW, ZoneOffset.UTC), TTL);
    }

    private static IncomingMessage telegram(String text) {
        return new IncomingMessage(Channel.TELEGRAM, "42", "100", text, null);
    }

    private void extractorReads(Extraction extraction) {
        when(listCategories.execute()).thenReturn(CATALOG);
        when(expenseExtractor.extract(any(), any())).thenReturn(extraction);
    }

    private void storingAnswers(String amount, String categoryName) {
        when(registerExpense.execute(any())).thenReturn(new RegisteredExpense(UUID.randomUUID(),
                new BigDecimal(amount), "ARS", null, categoryName, LocalDate.of(2026, 11, 19)));
    }

    private void waitingFor(PendingClarification question) {
        when(pendingClarifications.find(USER_ID, Channel.TELEGRAM)).thenReturn(Optional.of(question));
    }

    private static PendingClarification lunchWithoutAmount(Instant expiresAt) {
        return PendingClarification.missingAmount(USER_ID, Channel.TELEGRAM, "almuerzo con el equipo", "almuerzo",
                "alimentacion", expiresAt);
    }

    private static Extraction amountOnly(String amount) {
        return new Extraction.Found(new ExpenseDraft(new BigDecimal(amount), null, null));
    }

    private RegisterExpenseCommand registeredCommand() {
        var command = ArgumentCaptor.forClass(RegisterExpenseCommand.class);
        verify(registerExpense).execute(command.capture());
        return command.getValue();
    }

    private PendingClarification savedQuestion() {
        var question = ArgumentCaptor.forClass(PendingClarification.class);
        verify(pendingClarifications).save(question.capture());
        return question.getValue();
    }

    @Test
    void storesWhatTheModelReadAndAnswersWithASummary() {
        when(listCategories.execute()).thenReturn(CATALOG);
        when(expenseExtractor.extract("$15.000 carnicería", List.of("Alimentación", "Otros")))
                .thenReturn(new Extraction.Found(new ExpenseDraft(new BigDecimal("15000"), "carnicería", "alimentacion")));
        when(registerExpense.execute(any())).thenReturn(new RegisteredExpense(UUID.randomUUID(),
                new BigDecimal("15000.00"), "ARS", "carnicería", "Alimentación", LocalDate.of(2026, 11, 14)));

        String reply = registerExpenseFromText.replyTo(USER_ID, telegram("$15.000 carnicería"));

        RegisterExpenseCommand command = registeredCommand();
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.amount()).isEqualByComparingTo("15000");
        assertThat(command.description()).isEqualTo("carnicería");
        assertThat(command.categoryId()).isEqualTo(FOOD_ID);
        assertThat(command.spentOn()).isNull();
        assertThat(command.sourceChannel()).isEqualTo(Channel.TELEGRAM);
        assertThat(command.originalMessage()).isEqualTo("$15.000 carnicería");
        assertThat(reply).isEqualTo("Registré $15.000,00 en Alimentación (14/11/2026).");
    }

    @Test
    void leavesTheCategoryEmptyWhenTheModelNamedOneThatIsNotInTheCatalog() {
        extractorReads(new Extraction.Found(new ExpenseDraft(new BigDecimal("500"), null, "Inventada")));
        storingAnswers("500.00", "Otros");

        registerExpenseFromText.replyTo(USER_ID, telegram("500 en algo"));

        assertThat(registeredCommand().categoryId()).isNull(); // the expenses module turns null into Other
    }

    @Test
    void asksForTheAmountAndRemembersTheQuestion() {
        extractorReads(new Extraction.MissingAmount("almuerzo", "Alimentación"));

        String reply = registerExpenseFromText.replyTo(USER_ID, telegram("almuerzo con el equipo"));

        assertThat(reply).isEqualTo("¿Cuánto gastaste en almuerzo?");
        PendingClarification question = savedQuestion();
        assertThat(question.getOriginalMessage()).isEqualTo("almuerzo con el equipo");
        assertThat(question.getCategoryName()).isEqualTo("Alimentación");
        assertThat(question.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        verifyNoInteractions(registerExpense);
    }

    @Test
    void asksWhichAmountItWasWhenThereAreSeveral() {
        extractorReads(new Extraction.AmbiguousAmount(
                List.of(new BigDecimal("5000"), new BigDecimal("3000.50"), new BigDecimal("2000")), "super", null));

        String reply = registerExpenseFromText.replyTo(USER_ID, telegram("super 5000, 3000,50 o 2000"));

        assertThat(reply).isEqualTo("¿Fueron $5.000, $3.000,50 o $2.000?");
        assertThat(savedQuestion().getAmountCandidates()).hasSize(3);
        verifyNoInteractions(registerExpense);
    }

    @Test
    void asksToRephraseAndStoresNothingWhenThereIsNothingToAskAbout() {
        extractorReads(new Extraction.MissingAmount(null, null));

        assertThat(registerExpenseFromText.replyTo(USER_ID, telegram("hola")))
                .isEqualTo(RegisterExpenseFromText.REPHRASE);
        verifyNoInteractions(registerExpense);
        verify(pendingClarifications, never()).save(any());
    }

    @Test
    void anAmountAloneCompletesTheExpenseThatWasWaiting() {
        waitingFor(lunchWithoutAmount(NOW.plusSeconds(60)));
        extractorReads(amountOnly("8000"));
        storingAnswers("8000.00", "Alimentación");

        String reply = registerExpenseFromText.replyTo(USER_ID, telegram("8000"));

        RegisterExpenseCommand command = registeredCommand();
        assertThat(command.amount()).isEqualByComparingTo("8000");
        assertThat(command.description()).isEqualTo("almuerzo");
        assertThat(command.categoryId()).isEqualTo(FOOD_ID);
        assertThat(command.originalMessage()).isEqualTo("almuerzo con el equipo");
        assertThat(reply).isEqualTo("Registré $8.000,00 en Alimentación (19/11/2026).");
        verify(pendingClarifications).delete(USER_ID, Channel.TELEGRAM);
    }

    @Test
    void aNewExpenseIsStoredAndTheQuestionDropped() {
        waitingFor(lunchWithoutAmount(NOW.plusSeconds(60)));
        extractorReads(new Extraction.Found(new ExpenseDraft(new BigDecimal("3000"), "nafta", "Transporte")));
        storingAnswers("3000.00", "Otros");

        registerExpenseFromText.replyTo(USER_ID, telegram("3000 de nafta"));

        RegisterExpenseCommand command = registeredCommand();
        assertThat(command.description()).isEqualTo("nafta");
        assertThat(command.originalMessage()).isEqualTo("3000 de nafta");
        verify(pendingClarifications).delete(USER_ID, Channel.TELEGRAM);
    }

    @Test
    void aNewExpenseWithoutAmountReplacesTheQuestion() {
        waitingFor(lunchWithoutAmount(NOW.plusSeconds(60)));
        extractorReads(new Extraction.MissingAmount("nafta", null));

        String reply = registerExpenseFromText.replyTo(USER_ID, telegram("cargué nafta"));

        assertThat(reply).isEqualTo("¿Cuánto gastaste en nafta?");
        assertThat(savedQuestion().getDescription()).isEqualTo("nafta");
        verifyNoInteractions(registerExpense);
    }

    @Test
    void anythingElseRepeatsTheQuestion() {
        waitingFor(lunchWithoutAmount(NOW.plusSeconds(60)));
        extractorReads(new Extraction.MissingAmount(null, null));

        String reply = registerExpenseFromText.replyTo(USER_ID, telegram("no sé"));

        assertThat(reply).isEqualTo("No entendí el monto. ¿Cuánto gastaste en almuerzo?");
        verifyNoInteractions(registerExpense);
        verify(pendingClarifications, never()).save(any());
        verify(pendingClarifications, never()).delete(any(), any());
    }

    @Test
    void anAnswerTheExpenseRulesRejectRepeatsTheQuestionAndKeepsIt() {
        waitingFor(lunchWithoutAmount(NOW.plusSeconds(60)));
        extractorReads(amountOnly("0"));
        when(registerExpense.execute(any())).thenThrow(new IllegalArgumentException("Amount must be positive"));

        String reply = registerExpenseFromText.replyTo(USER_ID, telegram("0"));

        assertThat(reply).isEqualTo("No entendí el monto. ¿Cuánto gastaste en almuerzo?");
        verify(pendingClarifications, never()).delete(any(), any());
    }

    @Test
    void anExpiredQuestionIsIgnored() {
        waitingFor(lunchWithoutAmount(NOW)); // expiry is exclusive
        extractorReads(amountOnly("8000"));
        storingAnswers("8000.00", "Otros");

        registerExpenseFromText.replyTo(USER_ID, telegram("8000"));

        RegisterExpenseCommand command = registeredCommand();
        assertThat(command.description()).isNull();
        assertThat(command.originalMessage()).isEqualTo("8000");
    }

    @Test
    void asksToRephraseWhenTheExpenseRulesRejectWhatTheModelRead() {
        extractorReads(new Extraction.Found(new ExpenseDraft(BigDecimal.ZERO, null, null)));
        when(registerExpense.execute(any())).thenThrow(new IllegalArgumentException("Amount must be positive"));

        assertThat(registerExpenseFromText.replyTo(USER_ID, telegram("gasté 0")))
                .isEqualTo(RegisterExpenseFromText.REPHRASE);
    }

    @Test
    void tellsTheUserToTryAgainWhenTheExtractorFailsAndKeepsTheQuestion() {
        waitingFor(lunchWithoutAmount(NOW.plusSeconds(60)));
        when(listCategories.execute()).thenReturn(CATALOG);
        when(expenseExtractor.extract(any(), any())).thenThrow(new IllegalStateException("401 from the provider"));

        assertThat(registerExpenseFromText.replyTo(USER_ID, telegram("8000")))
                .isEqualTo(RegisterExpenseFromText.TRY_AGAIN);
        verifyNoInteractions(registerExpense);
        verify(pendingClarifications, never()).delete(any(), any());
    }

    @Test
    void asksForTextWhenTheMessageOnlyCarriesAnAttachment() {
        var photo = new Attachment(AttachmentType.IMAGE, "image/jpeg", new byte[]{1});
        var message = new IncomingMessage(Channel.TELEGRAM, "42", "101", null, List.of(photo));

        assertThat(registerExpenseFromText.replyTo(USER_ID, message)).isEqualTo(RegisterExpenseFromText.ONLY_TEXT);
        verifyNoInteractions(listCategories, expenseExtractor, registerExpense, pendingClarifications);
    }
}
