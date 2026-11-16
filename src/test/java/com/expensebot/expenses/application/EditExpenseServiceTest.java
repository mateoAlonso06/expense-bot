package com.expensebot.expenses.application;

import com.expensebot.accounts.Channel;
import com.expensebot.expenses.EditExpenseCommand;
import com.expensebot.expenses.ExpenseNotFoundException;
import com.expensebot.expenses.domain.Category;
import com.expensebot.expenses.domain.Expense;
import com.expensebot.expenses.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class EditExpenseServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 13);
    private static final Currency ARS = Currency.getInstance("ARS");
    private static final Category OTHER = Category.restore(Category.OTHER_ID, "Otros", true);
    private static final Category FOOD = Category.restore(UUID.randomUUID(), "Alimentación", false);

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @InjectMocks
    private EditExpenseService service;

    private final UUID userId = UUID.randomUUID();
    private final Expense expense = Expense.restore(UUID.randomUUID(), userId, Category.OTHER_ID,
            new Money(new BigDecimal("15000"), ARS), "carnicería", DAY, Channel.TELEGRAM, "$15.000 carnicería");

    private void theExpenseIsStored() {
        when(expenseRepository.findByIdAndUserId(expense.getId(), userId)).thenReturn(Optional.of(expense));
    }

    private EditExpenseCommand edit(String amount, String description, UUID categoryId) {
        return new EditExpenseCommand(userId, expense.getId(), amount == null ? null : new BigDecimal(amount),
                description, categoryId);
    }

    @Test
    void changesOnlyTheGivenFieldsAndStoresThem() {
        theExpenseIsStored();
        when(categoryRepository.findById(Category.OTHER_ID)).thenReturn(Optional.of(OTHER));

        var result = service.execute(edit("12500", null, null));

        verify(expenseRepository).update(expense);
        assertThat(expense.getMoney().amount()).isEqualByComparingTo("12500");
        assertThat(expense.getDescription()).isEqualTo("carnicería");
        assertThat(expense.getCategoryId()).isEqualTo(Category.OTHER_ID);
        assertThat(result.amount()).isEqualByComparingTo("12500");
        assertThat(result.categoryName()).isEqualTo("Otros");
        assertThat(result.spentOn()).isEqualTo(DAY);
    }

    @Test
    void changesTheCategoryAndTheDescription() {
        theExpenseIsStored();
        when(categoryRepository.findById(FOOD.getId())).thenReturn(Optional.of(FOOD));

        var result = service.execute(edit(null, "asado", FOOD.getId()));

        assertThat(expense.getCategoryId()).isEqualTo(FOOD.getId());
        assertThat(result.description()).isEqualTo("asado");
        assertThat(result.categoryName()).isEqualTo("Alimentación");
        assertThat(result.amount()).isEqualByComparingTo("15000");
    }

    @Test
    void clearsTheDescriptionWhenItIsBlank() {
        theExpenseIsStored();
        when(categoryRepository.findById(Category.OTHER_ID)).thenReturn(Optional.of(OTHER));

        var result = service.execute(edit(null, " ", null));

        assertThat(result.description()).isNull();
    }

    @Test
    void keepsTheCurrencyOfTheExpense() {
        theExpenseIsStored();
        when(categoryRepository.findById(Category.OTHER_ID)).thenReturn(Optional.of(OTHER));

        var result = service.execute(edit("100", null, null));

        assertThat(result.currency()).isEqualTo("ARS");
    }

    @Test
    void failsWhenTheExpenseIsNotTheUsersOrDoesNotExist() {
        when(expenseRepository.findByIdAndUserId(expense.getId(), userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(edit("100", null, null)))
                .isInstanceOf(ExpenseNotFoundException.class);
        verify(expenseRepository, never()).update(any());
    }

    @Test
    void rejectsACategoryThatDoesNotExistAndStoresNothing() {
        theExpenseIsStored();
        var unknown = UUID.randomUUID();
        when(categoryRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(edit(null, null, unknown)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(expenseRepository, never()).update(any());
    }

    @Test
    void rejectsAnAmountThatIsNotPositiveAndStoresNothing() {
        theExpenseIsStored();

        assertThatThrownBy(() -> service.execute(edit("0", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(expenseRepository, never()).update(any());
    }

    @Test
    void requiresTheUserAndTheExpense() {
        assertThatThrownBy(() -> new EditExpenseCommand(null, UUID.randomUUID(), null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EditExpenseCommand(userId, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
