package com.expensebot.conversation.eval;

import com.expensebot.conversation.application.ExpenseDraft;

import java.util.Objects;

/**
 * What the extractor returned for one case.
 *
 * @param actual           the draft the extractor returned, or null when it read no expense
 * @param resolvedCategory the category the user would end up with: the model's name resolved the way the app does,
 *                         {@link ExtractionCase#OTHER} when it does not resolve, null when there is no draft
 */
public record ExtractionOutcome(ExtractionCase expected, ExpenseDraft actual, String resolvedCategory,
                                long latencyMillis) {

    public ExtractionOutcome {
        Objects.requireNonNull(expected, "Expected case is required");
        if ((actual == null) != (resolvedCategory == null)) {
            throw new IllegalArgumentException("A resolved category goes with a draft, and only with one");
        }
    }

    /** No expense where none was expected, or the same amount (15000 equals 15000.00). */
    public boolean amountCorrect() {
        if (!expected.expectsExpense()) {
            return actual == null;
        }
        return actual != null && actual.amount().compareTo(expected.amount()) == 0;
    }

    /** Only meaningful when an expense was expected; a missed expense counts as a wrong category. */
    public boolean categoryCorrect() {
        return expected.expectsExpense() && expected.category().equals(resolvedCategory);
    }

    /** The user would get {@code Other} although the case expects another category. */
    public boolean fellBackToOther() {
        return expected.expectsExpense() && !ExtractionCase.OTHER.equals(expected.category())
                && ExtractionCase.OTHER.equals(resolvedCategory);
    }

    public boolean failed() {
        return !amountCorrect() || (expected.expectsExpense() && !categoryCorrect());
    }
}
