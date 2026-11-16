package com.expensebot.expenses;

import com.expensebot.accounts.Channel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * What it takes to register an expense. Only {@code userId}, {@code amount}, {@code sourceChannel} and
 * {@code originalMessage} are required.
 *
 * @param categoryId null means the fallback category {@code Other}
 * @param spentOn    null means today
 */
public record RegisterExpenseCommand(
        UUID userId,
        BigDecimal amount,
        String description,
        UUID categoryId,
        LocalDate spentOn,
        Channel sourceChannel,
        String originalMessage) {
}
