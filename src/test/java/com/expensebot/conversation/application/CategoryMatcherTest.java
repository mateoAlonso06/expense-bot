package com.expensebot.conversation.application;

import com.expensebot.expenses.CategoryView;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class CategoryMatcherTest {
    private static final UUID FOOD = UUID.randomUUID();
    private static final UUID GAMES = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final List<CategoryView> CATEGORIES = List.of(
            new CategoryView(FOOD, "Alimentación"),
            new CategoryView(GAMES, "Juegos electrónicos"),
            new CategoryView(OTHER, "Otros"));

    @Test
    void findsTheCategoryByItsExactName() {
        assertThat(CategoryMatcher.match("Alimentación", CATEGORIES)).contains(FOOD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alimentación", "ALIMENTACIÓN", "alimentacion", "  Alimentación  "})
    void ignoresCaseAccentsAndSurroundingSpaces(String name) {
        assertThat(CategoryMatcher.match(name, CATEGORIES)).contains(FOOD);
    }

    @Test
    void ignoresRepeatedSpacesInsideTheName() {
        assertThat(CategoryMatcher.match("juegos   electronicos", CATEGORIES)).contains(GAMES);
    }

    @Test
    void findsACategoryTheUserCreated() {
        assertThat(CategoryMatcher.match("Juegos electrónicos", CATEGORIES)).contains(GAMES);
    }

    @Test
    void doesNotGuessWhenTheNameIsNotInTheCatalog() {
        assertThat(CategoryMatcher.match("Comida rápida", CATEGORIES)).isEmpty();
    }

    @Test
    void doesNotMatchAPartOfAName() {
        assertThat(CategoryMatcher.match("Alimentación y bebidas", CATEGORIES)).isEmpty();
        assertThat(CategoryMatcher.match("Juegos", CATEGORIES)).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void hasNoCategoryWhenTheModelGaveNone(String name) {
        assertThat(CategoryMatcher.match(name, CATEGORIES)).isEmpty();
    }

    @Test
    void hasNoCategoryWhenTheCatalogIsEmpty() {
        assertThat(CategoryMatcher.match("Alimentación", List.of())).isEmpty();
    }
}
