package com.expensebot.expenses.application;

import com.expensebot.expenses.RegisterExpenseCommand;
import com.expensebot.expenses.RegisterExpenseUseCase;
import com.expensebot.expenses.RegisteredExpense;
import com.expensebot.expenses.UnknownCategoryException;
import com.expensebot.expenses.domain.Category;
import com.expensebot.expenses.domain.Expense;
import com.expensebot.expenses.domain.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

@Service
public class RegisterExpenseService implements RegisterExpenseUseCase {
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;
    private final ZoneId zone;
    private final Currency currency;

    public RegisterExpenseService(ExpenseRepository expenseRepository,
                                  CategoryRepository categoryRepository,
                                  Clock clock,
                                  @Value("${expenses.time-zone:America/Argentina/Buenos_Aires}") ZoneId zone,
                                  @Value("${expenses.currency:ARS}") String currencyCode) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
        this.clock = clock;
        this.zone = zone;
        this.currency = Currency.getInstance(currencyCode); // a wrong code fails at startup, not on the first expense
    }

    @Override
    @Transactional
    public RegisteredExpense execute(RegisterExpenseCommand command) {
        Objects.requireNonNull(command, "Command is required");
        Objects.requireNonNull(command.amount(), "Amount is required");

        UUID categoryId = command.categoryId() != null ? command.categoryId() : Category.OTHER_ID;
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new UnknownCategoryException(categoryId));
        LocalDate spentOn = command.spentOn() != null ? command.spentOn() : today();

        Expense expense = toExpense(command, category, spentOn);
        expenseRepository.save(expense);
        return toRegisteredExpense(expense, category);
    }

    private Expense toExpense(RegisterExpenseCommand command, Category category, LocalDate spentOn) {
        return Expense.create(command.userId(), category.getId(), new Money(command.amount(), currency),
                command.description(), spentOn, command.sourceChannel(), command.originalMessage());
    }

    private static RegisteredExpense toRegisteredExpense(Expense expense, Category category) {
        return new RegisteredExpense(expense.getId(), expense.getMoney().amount(),
                expense.getMoney().currency().getCurrencyCode(), expense.getDescription(), category.getName(),
                expense.getSpentOn());
    }

    /** "Today" is the user's calendar day, not UTC's: at 22:00 in Buenos Aires it is already tomorrow in UTC. */
    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }
}
