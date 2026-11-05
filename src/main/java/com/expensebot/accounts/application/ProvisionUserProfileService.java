package com.expensebot.accounts.application;

import com.expensebot.accounts.ProvisionUserProfileUseCase;
import com.expensebot.accounts.domain.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProvisionUserProfileService implements ProvisionUserProfileUseCase {
    private final UserProfileRepository userProfileRepository;

    public ProvisionUserProfileService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Override
    @Transactional
    public void execute(UUID userId, String email) {
        userProfileRepository.createIfAbsent(UserProfile.create(userId, email));
    }
}
