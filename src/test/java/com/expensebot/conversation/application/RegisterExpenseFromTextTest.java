package com.expensebot.conversation.application;

import com.expensebot.accounts.Channel;
import com.expensebot.conversation.Attachment;
import com.expensebot.conversation.AttachmentType;
import com.expensebot.conversation.IncomingMessage;
import com.expensebot.expenses.CategoryView;
import com.expensebot.expenses.ListCategoriesUseCase;
import com.expensebot.expenses.RegisterExpenseCommand;
import com.expensebot.expenses.RegisterExpenseUseCase;
import com.expensebot.expenses.RegisteredExpense;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private ListCategoriesUseCase listCategories;
    @Mock
    private RegisterExpenseUseCase registerExpense;
    @Mock
    private ExpenseExtractor expenseExtractor;
    @InjectMocks
    private RegisterExpenseFromText registerExpenseFromText;

    private static IncomingMessage telegram(String text) {
        return new IncomingMessage(Channel.TELEGRAM, "42", "100", text, null);
    }

    private RegisterExpenseCommand registeredCommand() {
        var command = ArgumentCaptor.forClass(RegisterExpenseCommand.class);
        verify(registerExpense).execute(command.capture());
        return command.getValue();
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
        when(listCategories.execute()).thenReturn(CATALOG);
        when(expenseExtractor.extract(any(), any()))
                .thenReturn(new Extraction.Found(new ExpenseDraft(new BigDecimal("500"), null, "Inventada")));
        when(registerExpense.execute(any())).thenReturn(new RegisteredExpense(UUID.randomUUID(),
                new BigDecimal("500.00"), "ARS", null, "Otros", LocalDate.of(2026, 11, 14)));

        registerExpenseFromText.replyTo(USER_ID, telegram("500 en algo"));

        assertThat(registeredCommand().categoryId()).isNull(); // the expenses module turns null into Other
    }

    @Test
    void asksToRephraseAndStoresNothingWhenNoAmountWasRead() {
        when(listCategories.execute()).thenReturn(CATALOG);
        when(expenseExtractor.extract(any(), any())).thenReturn(new Extraction.MissingAmount(null, null));

        assertThat(registerExpenseFromText.replyTo(USER_ID, telegram("tomé un café")))
                .isEqualTo(RegisterExpenseFromText.REPHRASE);
        verifyNoInteractions(registerExpense);
    }

    @Test
    void asksToRephraseWhenTheExpenseRulesRejectWhatTheModelRead() {
        when(listCategories.execute()).thenReturn(CATALOG);
        when(expenseExtractor.extract(any(), any()))
                .thenReturn(new Extraction.Found(new ExpenseDraft(BigDecimal.ZERO, null, null)));
        when(registerExpense.execute(any())).thenThrow(new IllegalArgumentException("Amount must be positive"));

        assertThat(registerExpenseFromText.replyTo(USER_ID, telegram("gasté 0")))
                .isEqualTo(RegisterExpenseFromText.REPHRASE);
    }

    @Test
    void tellsTheUserToTryAgainWhenTheExtractorFailsAndStoresNothing() {
        when(listCategories.execute()).thenReturn(CATALOG);
        when(expenseExtractor.extract(any(), any())).thenThrow(new IllegalStateException("401 from the provider"));

        assertThat(registerExpenseFromText.replyTo(USER_ID, telegram("$15.000 carnicería")))
                .isEqualTo(RegisterExpenseFromText.TRY_AGAIN);
        verifyNoInteractions(registerExpense);
    }

    @Test
    void asksForTextWhenTheMessageOnlyCarriesAnAttachment() {
        var photo = new Attachment(AttachmentType.IMAGE, "image/jpeg", new byte[]{1});
        var message = new IncomingMessage(Channel.TELEGRAM, "42", "101", null, List.of(photo));

        assertThat(registerExpenseFromText.replyTo(USER_ID, message)).isEqualTo(RegisterExpenseFromText.ONLY_TEXT);
        verifyNoInteractions(listCategories, expenseExtractor, registerExpense);
    }
}
