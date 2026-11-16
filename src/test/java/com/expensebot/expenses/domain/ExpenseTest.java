package com.expensebot.expenses.domain;

import com.expensebot.accounts.Channel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class ExpenseTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 13);
    private static final Money TEN = new Money(BigDecimal.TEN, Currency.getInstance("ARS"));

    private static Expense expense(Money money, String description, String originalMessage) {
        return Expense.create(UUID.randomUUID(), Category.OTHER_ID, money, description, DAY,
                Channel.TELEGRAM, originalMessage);
    }

    @Test
    void createsAnExpenseWithItsOwnIdAndTheGivenData() {
        var userId = UUID.randomUUID();
        var money = new Money(new BigDecimal("15000"), Currency.getInstance("ARS"));

        var expense = Expense.create(userId, Category.OTHER_ID, money, "carnicería", DAY,
                Channel.TELEGRAM, "$15.000 carnicería");

        assertThat(expense.getId()).isNotNull();
        assertThat(expense.getUserId()).isEqualTo(userId);
        assertThat(expense.getCategoryId()).isEqualTo(Category.OTHER_ID);
        assertThat(expense.getMoney()).isEqualTo(money);
        assertThat(expense.getDescription()).isEqualTo("carnicería");
        assertThat(expense.getSpentOn()).isEqualTo(DAY);
        assertThat(expense.getSourceChannel()).isEqualTo(Channel.TELEGRAM);
        assertThat(expense.getOriginalMessage()).isEqualTo("$15.000 carnicería");
    }

    @Test
    void rejectsAnAmountThatIsNotPositive() {
        var ars = Currency.getInstance("ARS");

        assertThatThrownBy(() -> expense(new Money(BigDecimal.ZERO, ars), null, "0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> expense(new Money(new BigDecimal("-5"), ars), null, "-5"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storesABlankDescriptionAsNothingAndTrimsTheOthers() {
        assertThat(expense(TEN, null, "10").getDescription()).isNull();
        assertThat(expense(TEN, "   ", "10").getDescription()).isNull();
        assertThat(expense(TEN, "  café ", "10").getDescription()).isEqualTo("café");
    }

    @Test
    void rejectsADescriptionLongerThanTheColumn() {
        String tooLong = "x".repeat(Expense.DESCRIPTION_MAX_LENGTH + 1);

        assertThatThrownBy(() -> expense(TEN, tooLong, "10")).isInstanceOf(IllegalArgumentException.class);
        assertThat(expense(TEN, "x".repeat(Expense.DESCRIPTION_MAX_LENGTH), "10").getDescription())
                .hasSize(Expense.DESCRIPTION_MAX_LENGTH);
    }

    @Test
    void requiresTheOriginalMessage() {
        assertThatThrownBy(() -> expense(TEN, null, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoresAStoredExpenseAsItWas() {
        var id = UUID.randomUUID();
        var userId = UUID.randomUUID();

        var expense = Expense.restore(id, userId, Category.OTHER_ID, TEN, "café", DAY, Channel.TELEGRAM, "10 café");

        assertThat(expense.getId()).isEqualTo(id);
        assertThat(expense.getUserId()).isEqualTo(userId);
        assertThat(expense.getMoney()).isEqualTo(TEN);
        assertThat(expense.getDescription()).isEqualTo("café");
        assertThat(expense.getSpentOn()).isEqualTo(DAY);
        assertThat(expense.getOriginalMessage()).isEqualTo("10 café");
    }

    @Test
    void changesTheAmount() {
        var expense = expense(TEN, null, "10");
        var twenty = new Money(new BigDecimal("20"), Currency.getInstance("ARS"));

        expense.changeAmount(twenty);

        assertThat(expense.getMoney()).isEqualTo(twenty);
    }

    @Test
    void rejectsANewAmountThatIsNotPositiveAndKeepsTheOldOne() {
        var expense = expense(TEN, null, "10");

        assertThatThrownBy(() -> expense.changeAmount(new Money(BigDecimal.ZERO, Currency.getInstance("ARS"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(expense.getMoney()).isEqualTo(TEN);
    }

    @Test
    void rejectsANewAmountInAnotherCurrency() {
        var expense = expense(TEN, null, "10");

        assertThatThrownBy(() -> expense.changeAmount(new Money(BigDecimal.TEN, Currency.getInstance("USD"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changesTheDescriptionWithTheSameRulesAsOnCreation() {
        var expense = expense(TEN, "café", "10");

        expense.changeDescription("  medialunas ");
        assertThat(expense.getDescription()).isEqualTo("medialunas");

        expense.changeDescription(" ");
        assertThat(expense.getDescription()).isNull();

        assertThatThrownBy(() -> expense.changeDescription("x".repeat(Expense.DESCRIPTION_MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changesTheCategory() {
        var expense = expense(TEN, null, "10");
        var food = UUID.randomUUID();

        expense.changeCategory(food);

        assertThat(expense.getCategoryId()).isEqualTo(food);
        assertThatThrownBy(() -> expense.changeCategory(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void requiresEveryOtherField() {
        var user = UUID.randomUUID();
        assertThatThrownBy(() -> Expense.create(null, Category.OTHER_ID, TEN, null, DAY, Channel.TELEGRAM, "m"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Expense.create(user, null, TEN, null, DAY, Channel.TELEGRAM, "m"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Expense.create(user, Category.OTHER_ID, null, null, DAY, Channel.TELEGRAM, "m"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Expense.create(user, Category.OTHER_ID, TEN, null, null, Channel.TELEGRAM, "m"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Expense.create(user, Category.OTHER_ID, TEN, null, DAY, null, "m"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Expense.create(user, Category.OTHER_ID, TEN, null, DAY, Channel.TELEGRAM, null))
                .isInstanceOf(NullPointerException.class);
    }
}
