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
    void recognizesACodeHoweverItWasTyped() {
        assertThat(LinkCodeFormat.looksLikeCode("K7M4-9QXT")).isTrue();
        assertThat(LinkCodeFormat.looksLikeCode("  k7m49qxt \n")).isTrue();
    }

    @Test
    void doesNotTakeAnOrdinaryMessageForACode() {
        assertThat(LinkCodeFormat.looksLikeCode("gasté 500 en el super")).isFalse();
        assertThat(LinkCodeFormat.looksLikeCode("")).isFalse();
        assertThat(LinkCodeFormat.looksLikeCode("K7M4-9QX")).isFalse(); // one character short
        assertThat(LinkCodeFormat.looksLikeCode("K7M4-9QXTT")).isFalse(); // one character too many
        assertThat(LinkCodeFormat.looksLikeCode("K7M4-9QX0")).isFalse(); // 0 is not in the alphabet
        assertThat(LinkCodeFormat.looksLikeCode("12345678")).isFalse(); // 1 is not in the alphabet
    }

    // The expected value comes from outside this code: printf %s K7M49QXT | sha256sum. Computing it with
    // LinkCodeFormat.hash itself would make the test pass even if hash returned the wrong thing.
    @Test
    void hashesTheNormalizedCodeWithSha256() {
        assertThat(LinkCodeFormat.hash("k7m4-9qxt"))
                .isEqualTo("ec52424e5226ea952f596ee33a7c71c99db0a1c38596e4ac4edbd591b1b85ffc");
    }

    @Test
    void hashesEveryWayOfTypingTheSameCodeToTheSameValue() {
        String hash = LinkCodeFormat.hash("K7M4-9QXT");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(LinkCodeFormat.hash("k7m49qxt")).isEqualTo(hash);
        assertThat(LinkCodeFormat.hash(" K7M4-9QXU ")).isNotEqualTo(hash);
    }
}
