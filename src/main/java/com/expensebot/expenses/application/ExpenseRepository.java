package com.expensebot.expenses.application;

import com.expensebot.accounts.Channel;
import com.expensebot.expenses.RegisteredExpense;
import com.expensebot.expenses.domain.Expense;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseRepository {
    void save(Expense expense);

    /** Empty when the expense does not exist or belongs to another user: the caller cannot tell them apart. */
    Optional<Expense> findByIdAndUserId(UUID id, UUID userId);

    /** Stores the editable fields (amount, description, category) of an expense that already exists. */
    void update(Expense expense);

    /**
     * A read model, not domain objects: the caller only shows them, and one query brings the category names too.
     *
     * @return the user's expenses stored through {@code channel} at {@code since} or later, newest first
     */
    List<RegisteredExpense> findRecent(UUID userId, Channel channel, Instant since);
}
