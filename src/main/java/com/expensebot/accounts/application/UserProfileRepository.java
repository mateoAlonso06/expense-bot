package com.expensebot.accounts.application;

import com.expensebot.accounts.domain.UserProfile;

public interface UserProfileRepository {

    /**
     * Stores the profile unless one with the same id already exists. Must be atomic so that
     * concurrent calls for the same id leave exactly one row.
     */
    void createIfAbsent(UserProfile profile);
}
