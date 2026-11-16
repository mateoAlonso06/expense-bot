package com.expensebot.expenses;

/** Stores an expense for a user, filling in the category, date and currency the caller did not provide. */
public interface RegisterExpenseUseCase {

    /**
     * @throws IllegalArgumentException if the amount is invalid or the category does not exist
     */
    RegisteredExpense execute(RegisterExpenseCommand command);
}
