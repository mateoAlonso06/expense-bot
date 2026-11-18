package com.expensebot.conversation.eval;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * One labeled message of the extraction dataset: what a person says the extractor should read from it.
 *
 * @param amount          null when no expense can be read from the message
 * @param description     what was bought, for a human reading the report; it is not scored
 * @param category        the category the user should end up with; null exactly when the amount is null. A message
 *                        that does not say what was bought expects {@link #OTHER}, the app's fallback
 * @param extraCategories categories the user created, offered to the model next to the base catalog
 * @param tags            the kinds of difficulty the case covers, for the per-tag breakdown
 */
public record ExtractionCase(String id, String message, BigDecimal amount, String description, String category,
                             List<String> extraCategories, List<String> tags) {

    /** The catalog Flyway seeds, in the same order. */
    public static final List<String> BASE_CATEGORIES =
            List.of("Alimentación", "Vivienda", "Transporte", "Salud", "Ocio", "Servicios", "Otros");
    /** Display name of the fallback category ({@code Other} in code and docs). */
    public static final String OTHER = "Otros";

    public ExtractionCase {
        Objects.requireNonNull(id, "Id is required");
        Objects.requireNonNull(message, "Message is required");
        extraCategories = extraCategories == null ? List.of() : List.copyOf(extraCategories);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public boolean expectsExpense() {
        return amount != null;
    }

    /** The category names the model is offered for this message. */
    public List<String> offeredCategories() {
        return Stream.concat(BASE_CATEGORIES.stream(), extraCategories.stream()).toList();
    }
}
