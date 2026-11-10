package com.expensebot.accounts.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class LinkCodeFormatTest {

    @Test
    void generatesEightCharactersGroupedInFours() {
        String code = LinkCodeFormat.generate(new SecureRandom());

        assertThat(code).matches("[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}");
    }

    @Test
    void generatesADifferentCodeEachTime() {
        var random = new SecureRandom();

        assertThat(LinkCodeFormat.generate(random)).isNotEqualTo(LinkCodeFormat.generate(random));
    }

    @Test
    void normalizesWhatTheUserTyped() {
        assertThat(LinkCodeFormat.normalize("  k7m4-9qxt \n")).isEqualTo("K7M49QXT");
    }

    @Test
    void hashesEveryWayOfTypingTheSameCodeToTheSameValue() {
        String hash = LinkCodeFormat.hash("K7M4-9QXT");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(LinkCodeFormat.hash("k7m49qxt")).isEqualTo(hash);
        assertThat(LinkCodeFormat.hash(" K7M4-9QXU ")).isNotEqualTo(hash);
    }
}
