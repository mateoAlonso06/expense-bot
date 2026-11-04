package com.expensebot.accounts;

import java.util.UUID;

/**
 * Makes sure a business profile exists for an authenticated user. Safe to call on every request:
 * it creates the profile the first time and does nothing afterwards.
 */
public interface ProvisionUserProfileUseCase {
    void execute(UUID userId, String email);
}
