package com.expensebot.expenses.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTest {

    @Test
    void isIdentifiedByIdNotByName() {
        var id = UUID.randomUUID();

        assertThat(Category.restore(id, "Food", false)).isEqualTo(Category.restore(id, "Comida", false));
        assertThat(Category.restore(id, "Food", false)).isNotEqualTo(Category.restore(UUID.randomUUID(), "Food", false));
    }
}
