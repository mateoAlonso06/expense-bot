package com.expensebot.conversation.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * What the {@link ExpenseExtractor} read from a message. It only reports what it found; the caller decides what to do
 * (store, ask for the amount, or ask the user to rephrase).
 * <p>
 * Sealed: these are the only possible results, so a {@code switch} over them is checked by the compiler.
 */
public sealed interface Extraction {

    /** A clear amount: the expense can be stored. */
    record Found(ExpenseDraft draft) implements Extraction {
        public Found {
            Objects.requireNonNull(draft, "Draft is required");
        }
    }

    /**
     * No amount in the message.
     *
     * @param description  what was bought, or null when the message does not say that either
     * @param categoryName the category the model picked, or null
     */
    record MissingAmount(String description, String categoryName) implements Extraction {
    }

    /**
     * Two or more amounts and the message does not say which one was spent.
     *
     * @param candidates the distinct amounts, in the order they appear in the message
     */
    record AmbiguousAmount(List<BigDecimal> candidates, String description, String categoryName)
            implements Extraction {
        public AmbiguousAmount {
            candidates = List.copyOf(candidates);
            if (candidates.size() < 2) {
                throw new IllegalArgumentException("An ambiguous amount needs at least two candidates");
            }
        }
    }

    /** The model did not answer in the expected format, so nothing can be read from it. */
    record Unreadable() implements Extraction {
    }
}
