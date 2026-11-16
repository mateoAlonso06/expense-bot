package com.expensebot.expenses;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * A partial edit: a null field stays as it is. A blank description clears it. There is no field for the date, the
 * source channel or the original message because they never change.
 */
public record EditExpenseCommand(
        UUID userId,
        UUID expenseId,
        BigDecimal amount,
        String description,
        UUID categoryId) {

    public EditExpenseCommand {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(expenseId, "Expense id is required");
    }
}
