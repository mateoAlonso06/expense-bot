package com.expensebot.accounts;

import java.time.Instant;

/**
 * A freshly issued link code. {@code code} is the only time the plain text exists: the database keeps its hash.
 */
public record IssuedLinkCode(String code, Instant expiresAt) {
}
