package com.expensebot.accounts;

import java.util.UUID;

/** Issues a single-use, short-lived code the user sends to the bot to link a channel identity to their account. */
public interface RequestLinkCodeUseCase {
    IssuedLinkCode execute(UUID userId);
}
