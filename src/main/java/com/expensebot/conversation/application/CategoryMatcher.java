package com.expensebot.conversation.application;

import com.expensebot.expenses.CategoryView;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Turns the category name the model wrote into the id of a catalog category. The model is asked for an exact name,
 * but it is only asked, not forced: it may change the case, drop an accent or invent a name. So the comparison
 * ignores case, accents and extra spaces, and anything that still does not match is "no category" (the expense then
 * falls back to {@code Other}). There is no fuzzy matching: a wrong guess would file the expense under the wrong
 * category, which is worse than {@code Other}.
 */
final class CategoryMatcher {
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private CategoryMatcher() {
    }

    /**
     * @return the id of the first category whose name matches, or empty if the name is null, blank or unknown. Known
     * limitation: if two categories normalize to the same text, the first one wins (see the ODD task file).
     */
    static Optional<UUID> match(String name, List<CategoryView> categories) {
        if (name == null) {
            return Optional.empty();
        }
        String wanted = normalize(name);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        return categories.stream()
                .filter(category -> normalize(category.name()).equals(wanted))
                .map(CategoryView::id)
                .findFirst();
    }

    /** "  Alimentación " and "alimentacion" become the same text: NFD splits "ó" into "o" + an accent mark. */
    private static String normalize(String text) {
        String withoutAccents = ACCENTS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(withoutAccents.strip()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }
}
