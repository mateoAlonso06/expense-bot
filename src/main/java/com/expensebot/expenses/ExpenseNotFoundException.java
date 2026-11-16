package com.expensebot.expenses;

import java.util.UUID;

/** The expense does not exist, or it belongs to another user: on purpose, the two cases look the same. */
public class ExpenseNotFoundException extends RuntimeException {
    public ExpenseNotFoundException(UUID expenseId) {
        super("Expense not found: " + expenseId);
    }
}
