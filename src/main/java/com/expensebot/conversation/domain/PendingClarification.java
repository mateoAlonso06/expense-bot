package com.expensebot.conversation.domain;

import com.expensebot.accounts.Channel;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A question the bot asked about an expense and is waiting for: how much was spent, or which of several amounts it
 * was. A conversation (a user on a channel) has at most one; asking again replaces it.
 */
@Getter
public class PendingClarification {
    private final UUID userId;
    private final Channel channel;
    /** The message that started the expense; the stored expense keeps it, not the answer. */
    private final String originalMessage;
    private final String description;
    private final String categoryName;
    /** Empty when the amount is missing; two or more distinct amounts when it is ambiguous. */
    private final List<BigDecimal> amountCandidates;
    private final Instant expiresAt;

    private PendingClarification(UUID userId, Channel channel, String originalMessage, String description,
                                 String categoryName, List<BigDecimal> amountCandidates, Instant expiresAt) {
        this.userId = userId;
        this.channel = channel;
        this.originalMessage = originalMessage;
        this.description = description;
        this.categoryName = categoryName;
        this.amountCandidates = amountCandidates;
        this.expiresAt = expiresAt;
    }

    /** The bot is missing the amount: it asks "how much did you spend on {description}?". */
    public static PendingClarification missingAmount(UUID userId, Channel channel, String originalMessage,
                                                     String description, String categoryName, Instant expiresAt) {
        Objects.requireNonNull(description, "Description is required to ask for the amount");
        if (description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank");
        }
        return create(userId, channel, originalMessage, description, categoryName, List.of(), expiresAt);
    }

    /** The bot read several amounts: it asks which one it was. */
    public static PendingClarification ambiguousAmount(UUID userId, Channel channel, String originalMessage,
                                                       String description, String categoryName,
                                                       List<BigDecimal> amountCandidates, Instant expiresAt) {
        Objects.requireNonNull(amountCandidates, "Amount candidates are required");
        List<BigDecimal> candidates = List.copyOf(amountCandidates);
        long distinct = candidates.stream().map(BigDecimal::stripTrailingZeros).distinct().count();
        if (distinct < 2 || distinct != candidates.size()) {
            throw new IllegalArgumentException("An ambiguous amount needs two or more distinct candidates");
        }
        return create(userId, channel, originalMessage, description, categoryName, candidates, expiresAt);
    }

    private static PendingClarification create(UUID userId, Channel channel, String originalMessage,
                                               String description, String categoryName,
                                               List<BigDecimal> amountCandidates, Instant expiresAt) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(channel, "Channel is required");
        Objects.requireNonNull(originalMessage, "Original message is required");
        Objects.requireNonNull(expiresAt, "Expiry is required");
        if (originalMessage.isBlank()) {
            throw new IllegalArgumentException("Original message must not be blank");
        }
        return new PendingClarification(userId, channel, originalMessage, description, categoryName,
                amountCandidates, expiresAt);
    }

    /** Rebuilds one already stored: no validation. */
    public static PendingClarification restore(UUID userId, Channel channel, String originalMessage,
                                               String description, String categoryName,
                                               List<BigDecimal> amountCandidates, Instant expiresAt) {
        return new PendingClarification(userId, channel, originalMessage, description, categoryName,
                List.copyOf(amountCandidates), expiresAt);
    }

    public boolean isAmbiguous() {
        return !amountCandidates.isEmpty();
    }

    /** Expiry is exclusive: at {@code expiresAt} the question no longer counts. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
