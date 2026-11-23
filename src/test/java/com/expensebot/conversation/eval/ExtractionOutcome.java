package com.expensebot.conversation.eval;

import com.expensebot.conversation.application.Extraction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * What the extractor returned for one case.
 *
 * @param actual           what the extractor reported
 * @param resolvedCategory the category the user would end up with: the model's name resolved the way the app does,
 *                         {@link ExtractionCase#OTHER} when it does not resolve; null only for an unreadable answer
 */
public record ExtractionOutcome(ExtractionCase expected, Extraction actual, String resolvedCategory,
                                long latencyMillis) {

    public ExtractionOutcome {
        Objects.requireNonNull(expected, "Expected case is required");
        Objects.requireNonNull(actual, "Actual extraction is required");
        if ((actual instanceof Extraction.Unreadable) != (resolvedCategory == null)) {
            throw new IllegalArgumentException("Only an unreadable answer has no resolved category");
        }
    }

    /**
     * The amount part of the answer leads to what the bot should do: the same amount, the same candidates (in any
     * order), a question for the amount, or a request to rephrase. For a correction, it changes exactly the fields the
     * case expects: the same amount (or none), a description only if one is expected, and no category unless one is
     * expected (the category value itself is scored by {@link #categoryCorrect()}).
     */
    public boolean amountCorrect() {
        return switch (expected.kind()) {
            case FOUND -> actual instanceof Extraction.Found(var draft)
                    && draft.amount().compareTo(expected.amount()) == 0;
            case AMBIGUOUS -> actual instanceof Extraction.AmbiguousAmount ambiguous
                    && sameAmounts(ambiguous.candidates(), expected.amountCandidates());
            case ASK_AMOUNT -> actual instanceof Extraction.MissingAmount(String description, var category)
                    && description != null;
            case CORRECTION -> actual instanceof Extraction.Correction correction
                    && sameAmount(correction.amount(), expected.amount())
                    && (correction.description() == null) == (expected.description() == null)
                    && (expected.category() != null || correction.categoryName() == null);
            case NOT_AN_EXPENSE -> actual instanceof Extraction.Unreadable
                    || actual instanceof Extraction.MissingAmount(String description, var category)
                    && description == null;
        };
    }

    /** Only meaningful when the case expects a category; a wrong kind of answer counts as a wrong category. */
    public boolean categoryCorrect() {
        return expected.category() != null && expected.category().equals(resolvedCategory);
    }

    /** The user would get {@code Other} although the case expects another category. */
    public boolean fellBackToOther() {
        return expected.category() != null && !ExtractionCase.OTHER.equals(expected.category())
                && ExtractionCase.OTHER.equals(resolvedCategory);
    }

    public boolean failed() {
        return !amountCorrect() || (expected.category() != null && !categoryCorrect());
    }

    private static boolean sameAmount(BigDecimal actual, BigDecimal expected) {
        return actual == null ? expected == null : expected != null && actual.compareTo(expected) == 0;
    }

    private static boolean sameAmounts(List<BigDecimal> actual, List<BigDecimal> expected) {
        return actual.size() == expected.size()
                && actual.stream().allMatch(a -> expected.stream().anyMatch(e -> e.compareTo(a) == 0));
    }
}
