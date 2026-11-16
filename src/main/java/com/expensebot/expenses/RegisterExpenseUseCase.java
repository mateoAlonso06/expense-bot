package com.expensebot.expenses;

/** Stores an expense for a user, filling in the category, date and currency the caller did not provide. */
public interface RegisterExpenseUseCase {

    /**
     * @throws UnknownCategoryException if the category does not exist
     * @throws IllegalArgumentException if the amount is invalid
     */
    RegisteredExpense execute(RegisterExpenseCommand command);
}
