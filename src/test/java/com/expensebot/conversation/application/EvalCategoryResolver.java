package com.expensebot.conversation.application;

import com.expensebot.expenses.CategoryView;

import java.util.List;
import java.util.UUID;

/**
 * Test-only bridge for the extraction eval: resolves a category name exactly as the app does
 * ({@link CategoryMatcher}, which is package-private), so the eval scores the category the user would end up with.
 */
public final class EvalCategoryResolver {

    private EvalCategoryResolver() {
    }

    /**
     * @return the offered name the model's answer matches, or {@code fallback} when it is null or matches none
     */
    public static String resolve(String modelCategory, List<String> offered, String fallback) {
        List<CategoryView> categories = offered.stream()
                .map(name -> new CategoryView(UUID.randomUUID(), name))
                .toList();
        return CategoryMatcher.match(modelCategory, categories)
                .flatMap(id -> categories.stream().filter(c -> c.id().equals(id)).map(CategoryView::name).findFirst())
                .orElse(fallback);
    }
}
