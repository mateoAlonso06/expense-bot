package com.expensebot.expenses.application;

import com.expensebot.accounts.Channel;
import com.expensebot.expenses.ListRecentExpensesUseCase;
import com.expensebot.expenses.RegisteredExpense;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ListRecentExpensesService implements ListRecentExpensesUseCase {
    private final ExpenseRepository expenseRepository;

    public ListRecentExpensesService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegisteredExpense> execute(UUID userId, Channel channel, Instant since) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(channel, "Channel is required");
        Objects.requireNonNull(since, "Since is required");
        return expenseRepository.findRecent(userId, channel, since);
    }
}
