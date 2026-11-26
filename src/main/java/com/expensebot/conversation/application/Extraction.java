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

    /**
     * The message says what was bought. While the bot waits for an answer, such a message is a new expense of its own,
     * not the answer.
     */
    boolean describesAPurchase();

    /**
     * A clear amount: the expense can be stored.
     */
    record Found(ExpenseDraft draft) implements Extraction {
        public Found {
            Objects.requireNonNull(draft, "Draft is required");
        }

        @Override
        public boolean describesAPurchase() {
            return draft.description() != null;
        }
    }

    /**
     * No amount in the message.
     *
     * @param description  what was bought, or null when the message does not say that either
     * @param categoryName the category the model picked, or null
     */
    record MissingAmount(String description, String categoryName) implements Extraction {

        @Override
        public boolean describesAPurchase() {
            return description != null;
        }
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

        @Override
        public boolean describesAPurchase() {
            return description != null;
        }
    }

    /**
     * The message corrects an expense already recorded instead of reporting a new one ("no, eran 15500", "categoría
     * ocio"). Each value is the new one the user gives, or null when the user does not change it.
     *
     * @param reference how the user names the expense to correct ("la nafta"), or null when the message does not say
     */
    record Correction(BigDecimal amount, String description, String categoryName, String reference)
            implements Extraction {

        /** A correction is about an expense already recorded, never a new purchase. */
        @Override
        public boolean describesAPurchase() {
            return false;
        }

        /** True when no editable field changes, for example "cambiá la fecha a ayer". */
        public boolean changesNothing() {
            return amount == null && description == null && categoryName == null;
        }

        /**
         * True for "eran 15000": a new amount and nothing else, not even which expense. While the bot waits for an
         * amount, such a message is the answer, not a correction.
         */
        public boolean onlyChangesTheAmount() {
            return amount != null && description == null && categoryName == null && reference == null;
        }
    }

    /**
     * The model did not answer in the expected format, so nothing can be read from it.
     */
    record Unreadable() implements Extraction {

        @Override
        public boolean describesAPurchase() {
            return false;
        }
    }
}
