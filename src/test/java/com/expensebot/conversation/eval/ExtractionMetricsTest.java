package com.expensebot.conversation.eval;

import com.expensebot.conversation.application.ExpenseDraft;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class ExtractionMetricsTest {

    private static ExtractionCase expense(String id, String amount, String category, String... tags) {
        return new ExtractionCase(id, "message " + id, new BigDecimal(amount), null, category, List.of(), List.of(tags));
    }

    private static ExtractionCase noExpense(String id, String... tags) {
        return new ExtractionCase(id, "message " + id, null, null, null, List.of(), List.of(tags));
    }

    private static ExtractionOutcome read(ExtractionCase expected, String amount, String resolvedCategory) {
        return new ExtractionOutcome(expected, new ExpenseDraft(new BigDecimal(amount), null, resolvedCategory),
                resolvedCategory, 10);
    }

    private static ExtractionOutcome nothingRead(ExtractionCase expected) {
        return new ExtractionOutcome(expected, null, null, 10);
    }

    @Test
    void comparesAmountsByValueNotByScale() {
        ExtractionOutcome outcome = read(expense("a", "15000", "Transporte"), "15000.00", "Transporte");

        assertThat(outcome.amountCorrect()).isTrue();
        assertThat(outcome.failed()).isFalse();
    }

    @Test
    void readingNothingIsRightOnlyWhenNoExpenseWasExpected() {
        assertThat(nothingRead(noExpense("a")).amountCorrect()).isTrue();
        assertThat(nothingRead(noExpense("a")).failed()).isFalse();

        ExtractionOutcome missed = nothingRead(expense("b", "100", "Ocio"));
        assertThat(missed.amountCorrect()).isFalse();
        assertThat(missed.categoryCorrect()).isFalse();
    }

    @Test
    void readingAnExpenseWhereThereIsNoneIsWrong() {
        assertThat(read(noExpense("a"), "1", "Otros").amountCorrect()).isFalse();
    }

    @Test
    void fallingBackToOtherCountsOnlyWhenAnotherCategoryWasExpected() {
        assertThat(read(expense("a", "100", "Salud"), "100", "Otros").fellBackToOther()).isTrue();
        assertThat(read(expense("b", "100", "Otros"), "100", "Otros").fellBackToOther()).isFalse();
        assertThat(read(expense("c", "100", "Salud"), "100", "Ocio").fellBackToOther()).isFalse();
    }

    @Test
    void scoresEachMetricOverItsOwnCases() {
        List<ExtractionOutcome> outcomes = List.of(
                read(expense("ok", "100", "Salud", "plain"), "100", "Salud"),
                read(expense("wrong-category", "200", "Ocio", "brand"), "200", "Otros"),
                read(expense("wrong-amount", "300", "Otros", "slang"), "3", "Otros"),
                nothingRead(noExpense("no-amount", "no-amount")));

        ExtractionMetrics metrics = ExtractionMetrics.score(outcomes);

        assertThat(metrics.cases()).isEqualTo(4);
        assertThat(metrics.amount()).isEqualTo(new ExtractionMetrics.Rate(3, 4));
        // the case without amount is not a category case
        assertThat(metrics.category()).isEqualTo(new ExtractionMetrics.Rate(2, 3));
        // only "ok" and "wrong-category" expect a category other than Otros
        assertThat(metrics.otherFallback()).isEqualTo(new ExtractionMetrics.Rate(1, 2));
        assertThat(metrics.failures()).extracting(o -> o.expected().id())
                .containsExactly("wrong-category", "wrong-amount");
    }

    @Test
    void breaksDownByTagSortedByName() {
        List<ExtractionOutcome> outcomes = List.of(
                read(expense("a", "100", "Salud", "slang", "brand"), "100", "Salud"),
                read(expense("b", "200", "Ocio", "slang"), "20", "Ocio"),
                nothingRead(noExpense("c", "no-amount")));

        ExtractionMetrics metrics = ExtractionMetrics.score(outcomes);

        assertThat(metrics.byTag()).containsOnlyKeys("brand", "no-amount", "slang");
        assertThat(metrics.byTag().keySet()).containsExactly("brand", "no-amount", "slang");
        assertThat(metrics.byTag().get("slang").amount()).isEqualTo(new ExtractionMetrics.Rate(1, 2));
        assertThat(metrics.byTag().get("slang").category()).isEqualTo(new ExtractionMetrics.Rate(2, 2));
        assertThat(metrics.byTag().get("no-amount").category().toString()).isEqualTo("n/a");
    }

    @Test
    void ratesPrintHitsTotalAndOneDecimalPercent() {
        assertThat(new ExtractionMetrics.Rate(2, 3)).hasToString("2/3 (66.7%)");
        assertThat(new ExtractionMetrics.Rate(0, 0).percent()).isNull();
    }

    @Test
    void aResolvedCategoryNeedsADraft() {
        assertThatThrownBy(() -> new ExtractionOutcome(noExpense("a"), null, "Otros", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
