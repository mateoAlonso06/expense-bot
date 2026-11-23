package com.expensebot.conversation.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ExtractionTest {
    private static final List<BigDecimal> TWO_AMOUNTS = List.of(new BigDecimal("5000"), new BigDecimal("3000"));

    @Test
    void describesAPurchaseOnlyWhenItSaysWhatWasBought() {
        assertThat(new Extraction.Found(new ExpenseDraft(BigDecimal.TEN, "nafta", null)).describesAPurchase())
                .isTrue();
        assertThat(new Extraction.MissingAmount("nafta", null).describesAPurchase()).isTrue();
        assertThat(new Extraction.AmbiguousAmount(TWO_AMOUNTS, "super", null).describesAPurchase()).isTrue();

        assertThat(new Extraction.Found(new ExpenseDraft(BigDecimal.TEN, null, null)).describesAPurchase())
                .isFalse();
        assertThat(new Extraction.MissingAmount(null, null).describesAPurchase()).isFalse();
        assertThat(new Extraction.AmbiguousAmount(TWO_AMOUNTS, null, null).describesAPurchase()).isFalse();
        assertThat(new Extraction.Unreadable().describesAPurchase()).isFalse();
        // a correction names an expense already recorded, not a new purchase
        assertThat(new Extraction.Correction(BigDecimal.TEN, "asado", null, "carnicería").describesAPurchase())
                .isFalse();
    }
}
