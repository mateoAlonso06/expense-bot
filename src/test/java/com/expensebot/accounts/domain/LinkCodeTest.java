package com.expensebot.accounts.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class LinkCodeTest {
    private static final UUID USER = UUID.randomUUID();
    private static final Instant EXPIRY = Instant.parse("2026-11-10T12:00:00Z");

    @Test
    void createsACodeWithItsOwnId() {
        var linkCode = LinkCode.create(USER, "abc123", EXPIRY);

        assertThat(linkCode.getId()).isNotNull();
        assertThat(linkCode.getUserId()).isEqualTo(USER);
        assertThat(linkCode.getCodeHash()).isEqualTo("abc123");
        assertThat(linkCode.getExpiresAt()).isEqualTo(EXPIRY);
    }

    @Test
    void rejectsMissingValues() {
        assertThatThrownBy(() -> LinkCode.create(null, "abc123", EXPIRY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> LinkCode.create(USER, null, EXPIRY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> LinkCode.create(USER, "abc123", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsABlankHash() {
        assertThatThrownBy(() -> LinkCode.create(USER, " ", EXPIRY)).isInstanceOf(IllegalArgumentException.class);
    }
}
