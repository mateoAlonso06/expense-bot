package com.expensebot.accounts.application;

import com.expensebot.accounts.Channel;
import com.expensebot.accounts.ResolveChannelIdentityUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ResolveChannelIdentityService implements ResolveChannelIdentityUseCase {
    private final ChannelIdentityRepository channelIdentityRepository;

    public ResolveChannelIdentityService(ChannelIdentityRepository channelIdentityRepository) {
        this.channelIdentityRepository = channelIdentityRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> execute(Channel channel, String externalId) {
        return channelIdentityRepository.findUserId(channel, externalId);
    }
}
