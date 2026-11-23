package com.expensebot.conversation.eval;

import com.expensebot.conversation.application.ExpenseDraft;
import com.expensebot.conversation.application.Extraction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class ExtractionMetricsTest {

    private static ExtractionCase expense(String id, String amount, String category, String... tags) {
        return new ExtractionCase(id, "message " + id, new BigDecimal(amount), null, null, category, null,
                List.of(tags), null, null);
    }

    private static ExtractionCase ambiguous(String id, String category, String... candidates) {
        return new ExtractionCase(id, "message " + id, null,
                List.of(candidates).stream().map(BigDecimal::new).toList(), null, category, null, List.of(), null, null);
    }

    private static ExtractionCase askAmount(String id, String description, String category, String... tags) {
        return new ExtractionCase(id, "message " + id, null, null, description, category, null, List.of(tags), null, null);
    }

    private static ExtractionCase notAnExpense(String id, String... tags) {
        return new ExtractionCase(id, "message " + id, null, null, null, null, null, List.of(tags), null, null);
    }

    private static ExtractionCase correction(String amount, String description, String category) {
        return new ExtractionCase("c", "message c", amount == null ? null : new BigDecimal(amount), null, description,
                category, null, List.of("correction"), true, null);
    }

    private static ExtractionOutcome corrected(ExtractionCase expected, String amount, String description,
                                               String category) {
        return new ExtractionOutcome(expected, new Extraction.Correction(
                amount == null ? null : new BigDecimal(amount), description, category, null),
                category == null ? "Otros" : category, 10);
    }

    private static ExtractionOutcome found(ExtractionCase expected, String amount, String resolvedCategory) {
        return new ExtractionOutcome(expected,
                new Extraction.Found(new ExpenseDraft(new BigDecimal(amount), null, resolvedCategory)),
                resolvedCategory, 10);
    }

    private static ExtractionOutcome missing(ExtractionCase expected, String description, String resolvedCategory) {
        return new ExtractionOutcome(expected, new Extraction.MissingAmount(description, resolvedCategory),
                resolvedCategory, 10);
    }

    private static ExtractionOutcome candidates(ExtractionCase expected, String... amounts) {
        return new ExtractionOutcome(expected, new Extraction.AmbiguousAmount(
                List.of(amounts).stream().map(BigDecimal::new).toList(), null, "Otros"), "Otros", 10);
    }

    private static ExtractionOutcome unreadable(ExtractionCase expected) {
        return new ExtractionOutcome(expected, new Extraction.Unreadable(), null, 10);
    }

    @Test
    void comparesAmountsByValueNotByScale() {
        ExtractionOutcome outcome = found(expense("a", "15000", "Transporte"), "15000.00", "Transporte");

        assertThat(outcome.amountCorrect()).isTrue();
        assertThat(outcome.failed()).isFalse();
    }

    @Test
    void anAmbiguousCaseNeedsTheSameCandidatesInAnyOrder() {
        ExtractionCase expected = ambiguous("a", "Otros", "5000", "3000");

        assertThat(candidates(expected, "3000", "5000.00").amountCorrect()).isTrue();
        assertThat(candidates(expected, "5000", "3500").amountCorrect()).isFalse();
        assertThat(found(expected, "5000", "Otros").amountCorrect()).isFalse();
    }

    @Test
    void askingForTheAmountNeedsAMissingAmountThatSaysWhatWasBought() {
        ExtractionCase expected = askAmount("a", "café", "Alimentación");

        assertThat(missing(expected, "un café", "Alimentación").amountCorrect()).isTrue();
        // without a description the bot would ask to rephrase, not for the amount
        assertThat(missing(expected, null, "Otros").amountCorrect()).isFalse();
        assertThat(found(expected, "1", "Alimentación").amountCorrect()).isFalse();
    }

    @Test
    void notAnExpenseAcceptsNothingReadOrAnUnreadableAnswer() {
        ExtractionCase expected = notAnExpense("a");

        assertThat(missing(expected, null, "Otros").amountCorrect()).isTrue();
        assertThat(unreadable(expected).amountCorrect()).isTrue();
        assertThat(unreadable(expected).failed()).isFalse();
        assertThat(missing(expected, "hola", "Otros").amountCorrect()).isFalse();
        assertThat(found(expected, "1", "Otros").amountCorrect()).isFalse();
    }

    @Test
    void aCorrectionChangesExactlyTheExpectedFields() {
        ExtractionCase newAmount = correction("15500", null, null);

        assertThat(corrected(newAmount, "15500.00", null, null).amountCorrect()).isTrue();
        assertThat(corrected(newAmount, "15000", null, null).amountCorrect()).isFalse();
        // a category the user did not ask for would overwrite the stored one
        assertThat(corrected(newAmount, "15500", null, "Transporte").amountCorrect()).isFalse();
        // so would a description
        assertThat(corrected(newAmount, "15500", "nafta", null).amountCorrect()).isFalse();
        // reading it as a new expense is wrong
        assertThat(found(newAmount, "15500", "Otros").amountCorrect()).isFalse();
    }

    @Test
    void aCorrectionThatChangesTheCategoryIsScoredOnItsValueToo() {
        ExtractionCase newCategory = correction(null, null, "Ocio");

        assertThat(corrected(newCategory, null, null, "Ocio").failed()).isFalse();
        ExtractionOutcome wrongCategory = corrected(newCategory, null, null, "Salud");
        assertThat(wrongCategory.amountCorrect()).isTrue();
        assertThat(wrongCategory.categoryCorrect()).isFalse();
        assertThat(wrongCategory.failed()).isTrue();
    }

    @Test
    void fallingBackToOtherCountsOnlyWhenAnotherCategoryWasExpected() {
        assertThat(found(expense("a", "100", "Salud"), "100", "Otros").fellBackToOther()).isTrue();
        assertThat(found(expense("b", "100", "Otros"), "100", "Otros").fellBackToOther()).isFalse();
        assertThat(found(expense("c", "100", "Salud"), "100", "Ocio").fellBackToOther()).isFalse();
        assertThat(missing(notAnExpense("d"), null, "Otros").fellBackToOther()).isFalse();
    }

    @Test
    void scoresEachMetricOverItsOwnCases() {
        List<ExtractionOutcome> outcomes = List.of(
                found(expense("ok", "100", "Salud", "plain"), "100", "Salud"),
                found(expense("wrong-category", "200", "Ocio", "brand"), "200", "Otros"),
                found(expense("wrong-amount", "300", "Otros", "slang"), "3", "Otros"),
                missing(askAmount("ask", "café", "Alimentación"), "café", "Alimentación"),
                missing(askAmount("ask-no-category", "algo", null), "algo", "Otros"),
                unreadable(notAnExpense("greeting")));

        ExtractionMetrics metrics = ExtractionMetrics.score(outcomes);

        assertThat(metrics.cases()).isEqualTo(6);
        assertThat(metrics.amount()).isEqualTo(new ExtractionMetrics.Rate(5, 6));
        // only the cases that expect a category
        assertThat(metrics.category()).isEqualTo(new ExtractionMetrics.Rate(3, 4));
        // "ok", "wrong-category" and "ask" expect a category other than Otros
        assertThat(metrics.otherFallback()).isEqualTo(new ExtractionMetrics.Rate(1, 3));
        assertThat(metrics.failures()).extracting(o -> o.expected().id())
                .containsExactly("wrong-category", "wrong-amount");
    }

    @Test
    void breaksDownByTagSortedByName() {
        List<ExtractionOutcome> outcomes = List.of(
                found(expense("a", "100", "Salud", "slang", "brand"), "100", "Salud"),
                found(expense("b", "200", "Ocio", "slang"), "20", "Ocio"),
                unreadable(notAnExpense("c", "not-an-expense")));

        ExtractionMetrics metrics = ExtractionMetrics.score(outcomes);

        assertThat(metrics.byTag().keySet()).containsExactly("brand", "not-an-expense", "slang");
        assertThat(metrics.byTag().get("slang").amount()).isEqualTo(new ExtractionMetrics.Rate(1, 2));
        assertThat(metrics.byTag().get("slang").category()).isEqualTo(new ExtractionMetrics.Rate(2, 2));
        assertThat(metrics.byTag().get("not-an-expense").category().toString()).isEqualTo("n/a");
    }

    @Test
    void ratesPrintHitsTotalAndOneDecimalPercent() {
        assertThat(new ExtractionMetrics.Rate(2, 3)).hasToString("2/3 (66.7%)");
        assertThat(new ExtractionMetrics.Rate(0, 0).percent()).isNull();
    }

    @Test
    void onlyAnUnreadableAnswerHasNoResolvedCategory() {
        assertThatThrownBy(() -> new ExtractionOutcome(notAnExpense("a"), new Extraction.Unreadable(), "Otros", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ExtractionOutcome(notAnExpense("a"),
                new Extraction.MissingAmount(null, null), null, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
