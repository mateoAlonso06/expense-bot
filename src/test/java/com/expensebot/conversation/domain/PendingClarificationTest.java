package com.expensebot.conversation.domain;

import com.expensebot.accounts.Channel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class PendingClarificationTest {
    private static final UUID USER = UUID.randomUUID();
    private static final Instant EXPIRY = Instant.parse("2026-11-19T12:10:00Z");

    private static List<BigDecimal> amounts(String... values) {
        return List.of(values).stream().map(BigDecimal::new).toList();
    }

    @Test
    void aMissingAmountHasNoCandidates() {
        var pending = PendingClarification.missingAmount(USER, Channel.TELEGRAM, "almuerzo", "almuerzo",
                "Alimentación", EXPIRY);

        assertThat(pending.isAmbiguous()).isFalse();
        assertThat(pending.getAmountCandidates()).isEmpty();
    }

    @Test
    void askingForTheAmountNeedsToKnowWhatWasBought() {
        assertThatThrownBy(() -> PendingClarification.missingAmount(USER, Channel.TELEGRAM, "gasté", null, null,
                EXPIRY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PendingClarification.missingAmount(USER, Channel.TELEGRAM, "gasté", " ", null,
                EXPIRY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anAmbiguousAmountKeepsACopyOfItsCandidates() {
        List<BigDecimal> candidates = new ArrayList<>(amounts("5000", "3000"));
        var pending = PendingClarification.ambiguousAmount(USER, Channel.TELEGRAM, "super 5000 o 3000", "super",
                null, candidates, EXPIRY);
        candidates.clear();

        assertThat(pending.isAmbiguous()).isTrue();
        assertThat(pending.getAmountCandidates()).containsExactlyElementsOf(amounts("5000", "3000"));
    }

    @Test
    void anAmbiguousAmountNeedsTwoOrMoreDistinctCandidates() {
        assertThatThrownBy(() -> PendingClarification.ambiguousAmount(USER, Channel.TELEGRAM, "m", null, null,
                amounts("5000"), EXPIRY)).isInstanceOf(IllegalArgumentException.class);
        // the same amount written twice is not a doubt
        assertThatThrownBy(() -> PendingClarification.ambiguousAmount(USER, Channel.TELEGRAM, "m", null, null,
                amounts("5000", "5000.00"), EXPIRY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresTheConversationTheMessageAndTheExpiry() {
        assertThatThrownBy(() -> PendingClarification.missingAmount(null, Channel.TELEGRAM, "m", "d", null, EXPIRY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PendingClarification.missingAmount(USER, null, "m", "d", null, EXPIRY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PendingClarification.missingAmount(USER, Channel.TELEGRAM, " ", "d", null, EXPIRY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PendingClarification.missingAmount(USER, Channel.TELEGRAM, "m", "d", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void expiresAtItsExpiry() {
        var pending = PendingClarification.missingAmount(USER, Channel.TELEGRAM, "m", "d", null, EXPIRY);

        assertThat(pending.isExpiredAt(EXPIRY.minusSeconds(1))).isFalse();
        assertThat(pending.isExpiredAt(EXPIRY)).isTrue();
    }
}
