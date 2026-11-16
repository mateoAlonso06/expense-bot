package com.expensebot.expenses;

import java.util.UUID;

/**
 * The category the caller chose does not exist. A client error, unlike other {@link IllegalArgumentException}s,
 * which after the request validation can only mean a bug.
 */
public class UnknownCategoryException extends IllegalArgumentException {
    public UnknownCategoryException(UUID categoryId) {
        super("Unknown category: " + categoryId);
    }
}
