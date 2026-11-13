package com.expensebot.accounts.application;

import com.expensebot.accounts.Channel;
import com.expensebot.accounts.LinkChannelIdentityUseCase;
import com.expensebot.accounts.LinkResult;
import com.expensebot.accounts.domain.ChannelIdentity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class LinkChannelIdentityService implements LinkChannelIdentityUseCase {
    private final LinkCodeRepository linkCodeRepository;
    private final ChannelIdentityRepository channelIdentityRepository;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public LinkChannelIdentityService(LinkCodeRepository linkCodeRepository,
                                      ChannelIdentityRepository channelIdentityRepository,
                                      Clock clock,
                                      PlatformTransactionManager transactionManager) {
        this.linkCodeRepository = linkCodeRepository;
        this.channelIdentityRepository = channelIdentityRepository;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public LinkResult execute(Channel channel, String externalId, String text) {
        // Cheap and database-free: most messages are not codes and must reach the agent untouched. A message that
        // only carries an attachment has no text at all.
        if (text == null || !LinkCodeFormat.looksLikeCode(text)) {
            return LinkResult.NOT_A_CODE;
        }
        // Programmatic transaction (TransactionTemplate) because the outcome decides the commit: a rejected
        // identity has to undo the consumption of the code, which a plain @Transactional cannot do without throwing.
        return transaction.execute(status -> {
            Optional<UUID> userId = linkCodeRepository.consume(LinkCodeFormat.hash(text), clock.instant());
            if (userId.isEmpty()) {
                return LinkResult.INVALID_CODE;
            }
            boolean linked = channelIdentityRepository.saveIfAvailable(
                    ChannelIdentity.create(userId.get(), channel, externalId));
            if (!linked) {
                status.setRollbackOnly(); // the code was fine, so it must not be burned
                boolean alreadyYours = channelIdentityRepository.findUserId(channel, externalId)
                        .filter(userId.get()::equals)
                        .isPresent();
                return alreadyYours ? LinkResult.ALREADY_LINKED_TO_SAME_USER : LinkResult.CHANNEL_ALREADY_LINKED;
            }
            return LinkResult.LINKED;
        });
    }
}
