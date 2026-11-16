package com.expensebot.expenses.infrastructure.web;

import com.expensebot.expenses.domain.Expense;
import com.expensebot.expenses.domain.Money;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Body of a partial edit: a field left out stays as it is, a blank description clears it. Any other field (the date,
 * the channel, the original message, or a typo) is rejected instead of silently ignored.
 *
 * @param otherFields every field not declared above. Spring's JSON mapper ignores unknown fields by default (and a
 *                    global switch would also affect the Telegram and OpenAI payloads), so they are collected here
 *                    and refused when the body is read.
 */
record EditExpenseRequest(
        @Positive @Digits(integer = Money.MAX_PRECISION - Money.SCALE, fraction = Money.SCALE) BigDecimal amount,
        @Size(max = Expense.DESCRIPTION_MAX_LENGTH) String description,
        UUID categoryId,
        @JsonAnySetter Map<String, Object> otherFields) {

    EditExpenseRequest {
        if (otherFields != null && !otherFields.isEmpty()) {
            throw new IllegalArgumentException("Only amount, description and categoryId can be edited, not "
                    + otherFields.keySet());
        }
    }
}
