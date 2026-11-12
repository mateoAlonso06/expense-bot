package com.expensebot.accounts.application;

import com.expensebot.accounts.Channel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class ResolveChannelIdentityServiceTest {
    @Mock
    private ChannelIdentityRepository channelIdentityRepository;
    @InjectMocks
    private ResolveChannelIdentityService service;

    @Test
    void resolvesALinkedAccountToItsUser() {
        var userId = UUID.randomUUID();
        when(channelIdentityRepository.findUserId(Channel.TELEGRAM, "500")).thenReturn(Optional.of(userId));

        assertThat(service.execute(Channel.TELEGRAM, "500")).contains(userId);
    }

    @Test
    void resolvesAnUnlinkedAccountToNobody() {
        when(channelIdentityRepository.findUserId(Channel.TELEGRAM, "501")).thenReturn(Optional.empty());

        assertThat(service.execute(Channel.TELEGRAM, "501")).isEmpty();
    }
}
