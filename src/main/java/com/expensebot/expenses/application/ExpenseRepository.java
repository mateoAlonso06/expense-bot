package com.expensebot.expenses.application;

import com.expensebot.expenses.domain.Expense;

public interface ExpenseRepository {
    void save(Expense expense);
}
