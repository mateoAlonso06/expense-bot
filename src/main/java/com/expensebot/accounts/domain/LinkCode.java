package com.expensebot.accounts.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A link code as stored: only the hash of the code the user sees, never the code itself. */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LinkCode {
    @EqualsAndHashCode.Include
    private final UUID id;
    private final UUID userId;
    private final String codeHash;
    private final Instant expiresAt;

    private LinkCode(UUID id, UUID userId, String codeHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    /** A code that was just issued and has not been used. */
    public static LinkCode create(UUID userId, String codeHash, Instant expiresAt) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(codeHash, "Code hash is required");
        Objects.requireNonNull(expiresAt, "Expiry is required");
        if (codeHash.isBlank()) {
            throw new IllegalArgumentException("Code hash must not be blank");
        }
        return new LinkCode(UUID.randomUUID(), userId, codeHash, expiresAt);
    }
}
