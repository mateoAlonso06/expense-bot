package com.expensebot.accounts;

/** What happened to a message handed to {@link LinkChannelIdentityUseCase}. */
public enum LinkResult {
    /** The identity is now linked to the user who requested the code. */
    LINKED,
    /** The text looked like a code but is unknown, expired or already used (deliberately not told apart). */
    INVALID_CODE,
    /** The code was fine, but that channel account belongs to someone else or the user linked another account on it. */
    CHANNEL_ALREADY_LINKED,
    /** The code was fine, but that channel account is already linked to the user who owns the code. */
    ALREADY_LINKED_TO_SAME_USER,
    /** The text is not a link code at all, so it is a regular message for the rest of the application. */
    NOT_A_CODE
}
