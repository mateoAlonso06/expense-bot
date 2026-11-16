package com.expensebot.expenses.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class MoneyTest {
    private static final Currency ARS = Currency.getInstance("ARS");

    private static Money money(String amount) {
        return new Money(new BigDecimal(amount), ARS);
    }

    @Test
    void keepsTheAmountAndTheCurrency() {
        var money = money("15000");

        assertThat(money.amount()).isEqualTo(new BigDecimal("15000.00"));
        assertThat(money.currency()).isEqualTo(ARS);
    }

    @Test
    void alwaysHasTwoDecimals() {
        assertThat(money("10.5").amount()).isEqualTo(new BigDecimal("10.50"));
        assertThat(money("1E+3").amount()).isEqualTo(new BigDecimal("1000.00"));
    }

    @Test
    void rejectsMoreThanTwoRealDecimalsInsteadOfRounding() {
        assertThatThrownBy(() -> money("10.123")).isInstanceOf(IllegalArgumentException.class);
        assertThat(money("10.100").amount()).isEqualTo(new BigDecimal("10.10")); // trailing zeros are not decimals
    }

    @Test
    void rejectsAnAmountThatDoesNotFitTheColumn() {
        assertThat(money("99999999999999999.99").amount()).isNotNull(); // 19 digits: the maximum
        assertThatThrownBy(() -> money("100000000000000000.00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isEqualWhenAmountAndCurrencyAreEqualWhateverTheWayItWasWritten() {
        assertThat(money("10")).isEqualTo(money("10.00")).hasSameHashCodeAs(money("10.00"));
        assertThat(money("10")).isNotEqualTo(money("11"));
        assertThat(money("10")).isNotEqualTo(new Money(BigDecimal.TEN, Currency.getInstance("USD")));
    }

    @Test
    void knowsWhetherItIsPositive() {
        assertThat(money("0.01").isPositive()).isTrue();
        assertThat(money("0").isPositive()).isFalse();
        assertThat(money("-5").isPositive()).isFalse();
    }

    @Test
    void requiresAmountAndCurrency() {
        assertThatThrownBy(() -> new Money(null, ARS)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null)).isInstanceOf(NullPointerException.class);
    }
}
