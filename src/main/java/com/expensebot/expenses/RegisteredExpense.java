package com.expensebot.expenses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A stored expense (just registered or edited), with what is needed to show it back to the user. */
public record RegisteredExpense(
        UUID id,
        BigDecimal amount,
        String currency,
        String description,
        String categoryName,
        LocalDate spentOn) {
}
