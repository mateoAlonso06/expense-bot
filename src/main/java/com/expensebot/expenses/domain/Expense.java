package com.expensebot.expenses.domain;

import com.expensebot.accounts.Channel;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** An expense a user logged. Only the amount, the description and the category will ever be editable. */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Expense {
    public static final int DESCRIPTION_MAX_LENGTH = 255;

    @EqualsAndHashCode.Include
    private final UUID id;
    private final UUID userId;
    private final UUID categoryId;
    private final Money money;
    private final String description;
    private final LocalDate spentOn;
    private final Channel sourceChannel;
    private final String originalMessage;

    private Expense(UUID id, UUID userId, UUID categoryId, Money money, String description,
                    LocalDate spentOn, Channel sourceChannel, String originalMessage) {
        this.id = id;
        this.userId = userId;
        this.categoryId = categoryId;
        this.money = money;
        this.description = description;
        this.spentOn = spentOn;
        this.sourceChannel = sourceChannel;
        this.originalMessage = originalMessage;
    }

    /** A new expense. It must be for a positive amount. A blank description becomes null. */
    public static Expense create(UUID userId, UUID categoryId, Money money, String description, LocalDate spentOn,
                                 Channel sourceChannel, String originalMessage) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(categoryId, "Category id is required");
        Objects.requireNonNull(money, "Money is required");
        Objects.requireNonNull(spentOn, "Date is required");
        Objects.requireNonNull(sourceChannel, "Source channel is required");
        Objects.requireNonNull(originalMessage, "Original message is required");
        if (!money.isPositive()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return new Expense(UUID.randomUUID(), userId, categoryId, money, normalizeDescription(description),
                spentOn, sourceChannel, validOriginalMessage(originalMessage));
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String stripped = description.strip();
        if (stripped.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("Description must have at most " + DESCRIPTION_MAX_LENGTH + " characters");
        }
        return stripped;
    }

    private static String validOriginalMessage(String originalMessage) {
        if (originalMessage.isBlank()) {
            throw new IllegalArgumentException("Original message must not be blank");
        }
        return originalMessage;
    }
}
