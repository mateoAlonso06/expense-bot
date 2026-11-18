package com.expensebot.conversation.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class EvalCategoryResolverTest {

    private static final List<String> OFFERED = List.of("Alimentación", "Salud", "Otros");

    @Test
    void returnsTheOfferedNameTheAppWouldMatch() {
        assertThat(EvalCategoryResolver.resolve(" alimentacion ", OFFERED, "Otros")).isEqualTo("Alimentación");
    }

    @Test
    void fallsBackWhenTheNameIsUnknownOrMissing() {
        assertThat(EvalCategoryResolver.resolve("Prepaga", OFFERED, "Otros")).isEqualTo("Otros");
        assertThat(EvalCategoryResolver.resolve(null, OFFERED, "Otros")).isEqualTo("Otros");
    }
}
