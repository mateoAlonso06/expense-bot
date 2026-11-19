package com.expensebot.conversation.eval;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * One labeled message of the extraction dataset: what a person says the extractor should read from it.
 *
 * @param amount           the amount, when the message has one clear amount
 * @param amountCandidates every amount, when the message has several and does not say which one was spent
 * @param description      what was bought. Scored only as present or absent when the amount is missing (it decides
 *                         whether the bot asks for the amount); otherwise shown for a human, not scored
 * @param category         the category the user should end up with. Required when there is an amount or candidates
 *                         (a message that does not say what was bought expects {@link #OTHER}); optional when the
 *                         amount is missing; null when the message is not an expense
 * @param extraCategories  categories the user created, offered to the model next to the base catalog
 * @param tags             the kinds of difficulty the case covers, for the per-tag breakdown
 */
public record ExtractionCase(String id, String message, BigDecimal amount, List<BigDecimal> amountCandidates,
                             String description, String category, List<String> extraCategories, List<String> tags) {

    /** The catalog Flyway seeds, in the same order. */
    public static final List<String> BASE_CATEGORIES =
            List.of("Alimentación", "Vivienda", "Transporte", "Salud", "Ocio", "Servicios", "Otros");
    /** Display name of the fallback category ({@code Other} in code and docs). */
    public static final String OTHER = "Otros";

    /** What the bot should do with the message, derived from the labels. */
    public enum Kind {
        /** One clear amount: store it. */
        FOUND,
        /** Several amounts and no way to tell which one: ask which. */
        AMBIGUOUS,
        /** It says what was bought but not how much: ask for the amount. */
        ASK_AMOUNT,
        /** Neither amount nor what was bought: ask to rephrase. */
        NOT_AN_EXPENSE
    }

    public ExtractionCase {
        Objects.requireNonNull(id, "Id is required");
        Objects.requireNonNull(message, "Message is required");
        amountCandidates = amountCandidates == null ? List.of() : List.copyOf(amountCandidates);
        extraCategories = extraCategories == null ? List.of() : List.copyOf(extraCategories);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public Kind kind() {
        if (amount != null) {
            return Kind.FOUND;
        }
        if (!amountCandidates.isEmpty()) {
            return Kind.AMBIGUOUS;
        }
        return description != null ? Kind.ASK_AMOUNT : Kind.NOT_AN_EXPENSE;
    }

    /** The category names the model is offered for this message. */
    public List<String> offeredCategories() {
        return Stream.concat(BASE_CATEGORIES.stream(), extraCategories.stream()).toList();
    }
}
