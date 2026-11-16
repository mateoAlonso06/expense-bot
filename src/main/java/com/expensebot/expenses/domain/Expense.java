package com.expensebot.expenses.domain;

import com.expensebot.accounts.Channel;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * An expense a user logged. Only the amount, the description and the category are editable, through the
 * {@code change...} methods; everything else is fixed when the expense is created.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Expense {
    public static final int DESCRIPTION_MAX_LENGTH = 255;

    @EqualsAndHashCode.Include
    private final UUID id;
    private final UUID userId;
    private UUID categoryId;
    private Money money;
    private String description;
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
        return new Expense(UUID.randomUUID(), userId, categoryId, positive(money), normalizeDescription(description),
                spentOn, sourceChannel, validOriginalMessage(originalMessage));
    }

    /** An expense already stored: no validation, the data was valid when it was saved. */
    public static Expense restore(UUID id, UUID userId, UUID categoryId, Money money, String description,
                                  LocalDate spentOn, Channel sourceChannel, String originalMessage) {
        return new Expense(id, userId, categoryId, money, description, spentOn, sourceChannel, originalMessage);
    }

    /** The new amount must be positive and keep the expense's currency (there is no conversion). */
    public void changeAmount(Money newMoney) {
        Objects.requireNonNull(newMoney, "Money is required");
        if (!newMoney.currency().equals(money.currency())) {
            throw new IllegalArgumentException("The currency of an expense cannot change");
        }
        this.money = positive(newMoney);
    }

    /** Same rules as on creation: a blank description clears it, the rest is trimmed. */
    public void changeDescription(String newDescription) {
        this.description = normalizeDescription(newDescription);
    }

    /** Whether the category exists is checked by the caller, which has the catalog. */
    public void changeCategory(UUID newCategoryId) {
        this.categoryId = Objects.requireNonNull(newCategoryId, "Category id is required");
    }

    private static Money positive(Money money) {
        if (!money.isPositive()) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return money;
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
