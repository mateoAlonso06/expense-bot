package com.expensebot.expenses.application;

import com.expensebot.expenses.domain.Expense;

import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository {
    void save(Expense expense);

    /** Empty when the expense does not exist or belongs to another user: the caller cannot tell them apart. */
    Optional<Expense> findByIdAndUserId(UUID id, UUID userId);

    /** Stores the editable fields (amount, description, category) of an expense that already exists. */
    void update(Expense expense);
}
