package com.expensebot.expenses.application;

import com.expensebot.expenses.EditExpenseCommand;
import com.expensebot.expenses.EditExpenseUseCase;
import com.expensebot.expenses.ExpenseNotFoundException;
import com.expensebot.expenses.RegisteredExpense;
import com.expensebot.expenses.UnknownCategoryException;
import com.expensebot.expenses.domain.Category;
import com.expensebot.expenses.domain.Expense;
import com.expensebot.expenses.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class EditExpenseService implements EditExpenseUseCase {
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;

    public EditExpenseService(ExpenseRepository expenseRepository, CategoryRepository categoryRepository) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public RegisteredExpense execute(EditExpenseCommand command) {
        Objects.requireNonNull(command, "Command is required");
        Expense expense = expenseRepository.findByIdAndUserId(command.expenseId(), command.userId())
                .orElseThrow(() -> new ExpenseNotFoundException(command.expenseId()));

        if (command.amount() != null) {
            // The currency is the expense's own: an edit can fix the number, not convert it.
            expense.changeAmount(new Money(command.amount(), expense.getMoney().currency()));
        }
        if (command.description() != null) {
            expense.changeDescription(command.description());
        }
        if (command.categoryId() != null) {
            expense.changeCategory(command.categoryId());
        }
        // Loaded even when the category did not change: the answer carries its name.
        Category category = categoryRepository.findById(expense.getCategoryId())
                .orElseThrow(() -> new UnknownCategoryException(expense.getCategoryId()));

        expenseRepository.update(expense);
        return new RegisteredExpense(expense.getId(), expense.getMoney().amount(),
                expense.getMoney().currency().getCurrencyCode(), expense.getDescription(), category.getName(),
                expense.getSpentOn());
    }
}
