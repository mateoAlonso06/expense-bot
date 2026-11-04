package com.expensebot.accounts.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserProfile {
    @EqualsAndHashCode.Include
    private final UUID id;
    private final String email;

    private UserProfile(UUID id, String email) {
        this.id = id;
        this.email = email;
    }

    /** A profile that does not exist yet. The id is the identity provider's subject. */
    public static UserProfile create(UUID id, String email) {
        Objects.requireNonNull(id, "User id is required");
        Objects.requireNonNull(email, "Email is required");
        if (email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        return new UserProfile(id, email);
    }

    /** Rebuilds a profile that was already validated and stored. */
    public static UserProfile restore(UUID id, String email) {
        return new UserProfile(id, email);
    }
}
