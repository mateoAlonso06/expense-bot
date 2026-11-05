package com.expensebot.accounts.infrastructure.persistence;

import com.expensebot.accounts.application.UserProfileRepository;
import com.expensebot.accounts.domain.UserProfile;
import org.springframework.stereotype.Repository;

@Repository
public class UserProfilePersistenceAdapter implements UserProfileRepository {
    private final UserProfileJpaRepository userProfileJpaRepository;

    public UserProfilePersistenceAdapter(UserProfileJpaRepository userProfileJpaRepository) {
        this.userProfileJpaRepository = userProfileJpaRepository;
    }

    @Override
    public void createIfAbsent(UserProfile profile) {
        userProfileJpaRepository.insertIfAbsent(profile.getId(), profile.getEmail());
    }
}
