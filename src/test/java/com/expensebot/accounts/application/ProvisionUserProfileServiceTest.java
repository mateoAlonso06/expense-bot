package com.expensebot.accounts.application;

import com.expensebot.accounts.domain.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProvisionUserProfileServiceTest {
    @Mock
    private UserProfileRepository userProfileRepository;
    @InjectMocks
    private ProvisionUserProfileService service;

    @Test
    void asksTheRepositoryToCreateTheProfileIfAbsent() {
        var userId = UUID.randomUUID();

        service.execute(userId, "ana@example.com");

        var captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).createIfAbsent(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(userId);
        assertThat(captor.getValue().getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void rejectsABlankEmailWithoutTouchingTheRepository() {
        assertThatThrownBy(() -> service.execute(UUID.randomUUID(), " "))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(userProfileRepository);
    }

    @Test
    void rejectsAMissingUserIdWithoutTouchingTheRepository() {
        assertThatThrownBy(() -> service.execute(null, "ana@example.com"))
                .isInstanceOf(NullPointerException.class);

        verifyNoInteractions(userProfileRepository);
    }
}
