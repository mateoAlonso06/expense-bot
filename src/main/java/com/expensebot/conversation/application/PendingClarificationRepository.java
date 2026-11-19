package com.expensebot.conversation.application;

import com.expensebot.accounts.Channel;
import com.expensebot.conversation.domain.PendingClarification;

import java.util.Optional;
import java.util.UUID;

public interface PendingClarificationRepository {

    /** Stores the question, replacing the one the conversation had, if any. */
    void save(PendingClarification clarification);

    /** The conversation's question, expired or not: the caller decides with the clock. */
    Optional<PendingClarification> find(UUID userId, Channel channel);

    void delete(UUID userId, Channel channel);
}
