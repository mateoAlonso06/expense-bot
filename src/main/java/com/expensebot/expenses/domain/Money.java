package com.expensebot.expenses.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in a currency. A value object, so a record: it has no id, and two of them are equal when
 * amount and currency are equal. The amount is kept with exactly two decimals, so {@code 10} and {@code 10.00} are
 * the same money. Whether it may be zero or negative is up to who uses it (an expense must be positive, a total may
 * be zero).
 *
 * <p>More than two real decimals are rejected instead of rounded, because silently changing money is worse than
 * asking again. A currency must be a real ISO 4217 one: {@link Currency#getInstance(String)} checks that.
 */
public record Money(BigDecimal amount, Currency currency) {
    public static final int SCALE = 2;
    public static final int MAX_PRECISION = 19; // NUMERIC(19,2)

    // Compact constructor: runs on every creation, so no Money can exist without passing these checks.
    // Reassigning the parameter normalizes the field the record stores.
    public Money {
        Objects.requireNonNull(amount, "Amount is required");
        Objects.requireNonNull(currency, "Currency is required");
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("Amount must have at most two decimals");
        }
        amount = amount.setScale(SCALE);
        if (amount.precision() > MAX_PRECISION) {
            throw new IllegalArgumentException("Amount is too large");
        }
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }
}
