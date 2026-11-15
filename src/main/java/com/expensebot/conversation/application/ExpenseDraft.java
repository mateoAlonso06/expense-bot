package com.expensebot.conversation.application;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What was read from a message, before anything is validated or stored. Only the amount is guaranteed to be there.
 *
 * @param description    null when the message did not say what the money was spent on
 * @param categoryName   null when no category was clear; otherwise the name of one of the catalog categories offered
 */
public record ExpenseDraft(BigDecimal amount, String description, String categoryName) {
    public ExpenseDraft {
        Objects.requireNonNull(amount, "Amount is required");
    }
}
