package com.expensebot.expenses;

/** Changes the amount, description or category of one of the user's own expenses. Nothing else is editable. */
public interface EditExpenseUseCase {

    /**
     * @throws ExpenseNotFoundException if the expense does not exist or belongs to another user
     * @throws UnknownCategoryException if the new category does not exist
     * @throws IllegalArgumentException if the new amount is invalid
     */
    RegisteredExpense execute(EditExpenseCommand command);
}
