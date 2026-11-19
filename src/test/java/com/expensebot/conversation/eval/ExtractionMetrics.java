package com.expensebot.conversation.eval;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * The numbers of one eval run.
 *
 * @param amount        right amount outcome over every case: same amount, same candidates, asking for the amount, or
 *                      asking to rephrase, as the case expects
 * @param category      right category over the cases that expect one
 * @param otherFallback cases that got {@code Other} over the cases that expect another category; lower is better
 * @param byTag         amount and category per tag, sorted by tag name
 * @param failures      cases with a wrong amount or category, in dataset order
 */
public record ExtractionMetrics(int cases, Rate amount, Rate category, Rate otherFallback,
                                Map<String, TagMetrics> byTag, List<ExtractionOutcome> failures) {

    public static ExtractionMetrics score(List<ExtractionOutcome> outcomes) {
        List<ExtractionOutcome> withCategory = outcomes.stream().filter(o -> o.expected().category() != null).toList();
        List<ExtractionOutcome> expectingNonOther = withCategory.stream()
                .filter(o -> !ExtractionCase.OTHER.equals(o.expected().category()))
                .toList();

        Map<String, TagMetrics> byTag = new TreeMap<>();
        outcomes.stream()
                .flatMap(o -> o.expected().tags().stream())
                .distinct()
                .forEach(tag -> {
                    List<ExtractionOutcome> tagged = outcomes.stream()
                            .filter(o -> o.expected().tags().contains(tag))
                            .toList();
                    byTag.put(tag, new TagMetrics(
                            Rate.of(tagged, ExtractionOutcome::amountCorrect),
                            Rate.of(tagged.stream().filter(o -> o.expected().category() != null).toList(),
                                    ExtractionOutcome::categoryCorrect)));
                });

        return new ExtractionMetrics(
                outcomes.size(),
                Rate.of(outcomes, ExtractionOutcome::amountCorrect),
                Rate.of(withCategory, ExtractionOutcome::categoryCorrect),
                Rate.of(expectingNonOther, ExtractionOutcome::fellBackToOther),
                byTag,
                outcomes.stream().filter(ExtractionOutcome::failed).toList());
    }

    /** {@code hits} out of {@code total}. A total of zero means "nothing to measure", not 0%. */
    public record Rate(int hits, int total) {

        static Rate of(List<ExtractionOutcome> outcomes, Predicate<ExtractionOutcome> hit) {
            return new Rate((int) outcomes.stream().filter(hit).count(), outcomes.size());
        }

        /** Percentage with one decimal, or null when there is nothing to measure. */
        public Double percent() {
            return total == 0 ? null : Math.round(hits * 1000.0 / total) / 10.0;
        }

        @Override
        public String toString() {
            return total == 0 ? "n/a" : hits + "/" + total + " (" + percent() + "%)";
        }
    }

    /** @param category n/a when no case of the tag expects a category */
    public record TagMetrics(Rate amount, Rate category) {
    }
}
