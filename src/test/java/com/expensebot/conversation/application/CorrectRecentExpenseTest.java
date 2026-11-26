package com.expensebot.conversation.application;

import com.expensebot.accounts.Channel;
import com.expensebot.expenses.CategoryView;
import com.expensebot.expenses.EditExpenseCommand;
import com.expensebot.expenses.EditExpenseUseCase;
import com.expensebot.expenses.ExpenseNotFoundException;
import com.expensebot.expenses.ListRecentExpensesUseCase;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class CorrectRecentExpenseTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID FOOD_ID = UUID.randomUUID();
    private static final UUID FUN_ID = UUID.randomUUID();
    private static final List<CategoryView> CATALOG = List.of(
            new CategoryView(FOOD_ID, "Alimentación"),
            new CategoryView(FUN_ID, "Ocio"),
            new CategoryView(UUID.randomUUID(), "Otros"));
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final Duration WINDOW = Duration.ofMinutes(30);
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock
    private ListRecentExpensesUseCase listRecentExpenses;
    @Mock
    private EditExpenseUseCase editExpense;

    private CorrectRecentExpense correctRecentExpense;

    @BeforeEach
    void createTheService() {
        correctRecentExpense = new CorrectRecentExpense(listRecentExpenses, editExpense,
                Clock.fixed(NOW, ZoneOffset.UTC), WINDOW);
    }

    private static RegisteredExpense stored(String amount, String description, String categoryName) {
        return new RegisteredExpense(UUID.randomUUID(), new BigDecimal(amount), "ARS", description, categoryName,
                TODAY);
    }

    private static Extraction.Correction correction(String amount, String description, String category,
                                                    String reference) {
        return new Extraction.Correction(amount == null ? null : new BigDecimal(amount), description, category,
                reference);
    }

    /** The recent expenses, newest first, as the expenses module returns them. */
    private void recentAre(RegisteredExpense... expenses) {
        when(listRecentExpenses.execute(USER_ID, Channel.TELEGRAM, NOW.minus(WINDOW))).thenReturn(List.of(expenses));
    }

    private void editingAnswers(RegisteredExpense corrected) {
        when(editExpense.execute(any())).thenReturn(corrected);
    }

    private EditExpenseCommand editCommand() {
        var command = ArgumentCaptor.forClass(EditExpenseCommand.class);
        verify(editExpense).execute(command.capture());
        return command.getValue();
    }

    private String reply(Extraction.Correction correction) {
        return correctRecentExpense.replyTo(USER_ID, Channel.TELEGRAM, correction, CATALOG);
    }

    @Test
    void withoutAReferenceCorrectsTheMostRecentExpense() {
        var newest = stored("15000", "carnicería", "Alimentación");
        recentAre(newest, stored("3000", "uber", "Transporte"));
        editingAnswers(stored("15500", "carnicería", "Alimentación"));

        String reply = reply(correction("15500", null, null, null));

        EditExpenseCommand command = editCommand();
        assertThat(command.userId()).isEqualTo(USER_ID);
        assertThat(command.expenseId()).isEqualTo(newest.id());
        assertThat(command.amount()).isEqualByComparingTo("15500");
        assertThat(command.description()).isNull();
        assertThat(command.categoryId()).isNull();
        assertThat(reply).isEqualTo("Corregí carnicería: $15.500,00 en Alimentación (25/11/2026).");
    }

    @Test
    void changesTheCategoryByItsCatalogId() {
        recentAre(stored("15000", "carnicería", "Alimentación"));
        editingAnswers(stored("15000", "carnicería", "Ocio"));

        String reply = reply(correction(null, null, "ocio", null));

        assertThat(editCommand().categoryId()).isEqualTo(FUN_ID);
        assertThat(reply).isEqualTo("Corregí carnicería: $15.000,00 en Ocio (25/11/2026).");
    }

    @Test
    void withAReferenceCorrectsTheOnlyRecentExpenseItNamesEvenIfItIsNotTheLast() {
        var fuel = stored("5000", "Nafta YPF", "Transporte");
        recentAre(stored("15000", "carnicería", "Alimentación"), fuel);
        editingAnswers(stored("3500", "Nafta YPF", "Transporte"));

        // the article is dropped and case and accents are ignored
        String reply = reply(correction("3500", null, null, "la NAFTA"));

        assertThat(editCommand().expenseId()).isEqualTo(fuel.id());
        assertThat(reply).isEqualTo("Corregí Nafta YPF: $3.500,00 en Transporte (25/11/2026).");
    }

    @Test
    void asksWhichOneWhenTheReferenceNamesSeveralRecentExpenses() {
        recentAre(stored("5000", "nafta YPF", "Transporte"), stored("4000", "nafta Shell", "Transporte"));

        String reply = reply(correction("3500", null, null, "la nafta"));

        verifyNoInteractions(editExpense);
        assertThat(reply).isEqualTo("Encontré varios gastos recientes de «la nafta»: nafta YPF ($5.000), "
                + "nafta Shell ($4.000). Repetí la corrección nombrando cuál.");
    }

    @Test
    void listsTheRecentExpensesWhenTheReferenceNamesNone() {
        recentAre(stored("3000", "uber", "Transporte"), stored("800", null, "Otros"));

        String reply = reply(correction("3500", null, null, "la nafta"));

        verifyNoInteractions(editExpense);
        // an expense without a description shows its category
        assertThat(reply).isEqualTo(
                "No encontré un gasto reciente de «la nafta». Los recientes son: uber ($3.000), Otros ($800).");
    }

    @Test
    void aReferenceThatIsOnlyAnArticleIsNoReference() {
        var newest = stored("15000", "carnicería", "Alimentación");
        recentAre(newest, stored("3000", "uber", "Transporte"));
        editingAnswers(stored("15500", "carnicería", "Alimentación"));

        reply(correction("15500", null, null, "la"));

        assertThat(editCommand().expenseId()).isEqualTo(newest.id());
    }

    @Test
    void saysSoWhenThereIsNoRecentExpense() {
        recentAre();

        assertThat(reply(correction("15500", null, null, null))).isEqualTo(CorrectRecentExpense.NO_RECENT);
        verifyNoInteractions(editExpense);
    }

    @Test
    void onlyTheEditableFieldsCanChange() {
        // "cambiá la fecha a ayer": nothing editable, so nothing is even looked up
        assertThat(reply(correction(null, null, null, null))).isEqualTo(CorrectRecentExpense.NOT_EDITABLE);
        verifyNoInteractions(listRecentExpenses, editExpense);
    }

    @Test
    void listsTheCategoriesWhenTheNewOneIsNotInTheCatalog() {
        String reply = reply(correction(null, null, "mascotas", null));

        assertThat(reply).isEqualTo("No tengo la categoría «mascotas». Las disponibles son: Alimentación, Ocio, Otros.");
        verifyNoInteractions(listRecentExpenses, editExpense);
    }

    @Test
    void saysSoWhenTheExpenseRulesRejectTheNewValue() {
        recentAre(stored("15000", "carnicería", "Alimentación"));
        when(editExpense.execute(any())).thenThrow(new IllegalArgumentException("Amount must be greater than zero"));

        assertThat(reply(correction("0", null, null, null))).isEqualTo(CorrectRecentExpense.INVALID);
    }

    @Test
    void saysThereIsNothingRecentWhenTheExpenseWasDeletedMeanwhile() {
        var gone = stored("15000", "carnicería", "Alimentación");
        recentAre(gone);
        when(editExpense.execute(any())).thenThrow(new ExpenseNotFoundException(gone.id()));

        assertThat(reply(correction("15500", null, null, null))).isEqualTo(CorrectRecentExpense.NO_RECENT);
    }

    @Test
    void namesTheExpenseGenericallyWhenItHasNoDescription() {
        recentAre(stored("500", null, "Otros"));
        editingAnswers(stored("600", null, "Otros"));

        assertThat(reply(correction("600", null, null, null))).isEqualTo("Corregí el gasto: $600,00 en Otros (25/11/2026).");
    }
}
