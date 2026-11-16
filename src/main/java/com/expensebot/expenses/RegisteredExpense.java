package com.expensebot.expenses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A stored expense, with what the bot needs to summarize it back to the user. */
public record RegisteredExpense(
        UUID id,
        BigDecimal amount,
        String currency,
        String description,
        String categoryName,
        LocalDate spentOn) {
}
