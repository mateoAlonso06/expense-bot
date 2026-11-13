package com.expensebot.accounts.application;

import com.expensebot.accounts.IssuedLinkCode;
import com.expensebot.accounts.RequestLinkCodeUseCase;
import com.expensebot.accounts.domain.LinkCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class RequestLinkCodeService implements RequestLinkCodeUseCase {
    private final SecureRandom random = new SecureRandom();
    private final LinkCodeRepository linkCodeRepository;
    private final Clock clock;
    private final Duration ttl;

    public RequestLinkCodeService(LinkCodeRepository linkCodeRepository,
                                  Clock clock,
                                  @Value("${accounts.link-code.ttl:10m}") Duration ttl) {
        this.linkCodeRepository = linkCodeRepository;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    @Transactional
    public IssuedLinkCode execute(UUID userId) {
        String code = LinkCodeFormat.generate(random);
        Instant expiresAt = clock.instant().plus(ttl);
        linkCodeRepository.save(LinkCode.create(userId, LinkCodeFormat.hash(code), expiresAt));
        return new IssuedLinkCode(code, expiresAt);
    }
}
