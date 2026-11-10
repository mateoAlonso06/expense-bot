package com.expensebot.channels.infrastructure.persistence;

import com.expensebot.channels.application.ProcessedMessageRepository;
import com.expensebot.accounts.Channel;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedMessagePersistenceAdapter implements ProcessedMessageRepository {
    private final JdbcClient jdbcClient;

    public ProcessedMessagePersistenceAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // Plain SQL on purpose: nothing reads this table through JPA, and ON CONFLICT is PostgreSQL-only.
    // The affected-row count tells a new message (1) from a duplicate (0); received_at comes from the default.
    @Override
    public boolean registerIfNew(Channel channel, String eventId) {
        int inserted = jdbcClient
                .sql("INSERT INTO processed_message (channel, event_id) VALUES (:channel, :eventId) "
                        + "ON CONFLICT (channel, event_id) DO NOTHING")
                .param("channel", channel.name())
                .param("eventId", eventId)
                .update();
        return inserted == 1;
    }
}
