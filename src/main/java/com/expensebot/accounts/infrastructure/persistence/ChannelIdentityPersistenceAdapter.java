package com.expensebot.accounts.infrastructure.persistence;

import com.expensebot.accounts.application.ChannelIdentityRepository;
import com.expensebot.accounts.domain.ChannelIdentity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelIdentityPersistenceAdapter implements ChannelIdentityRepository {
    private final JdbcClient jdbcClient;

    public ChannelIdentityPersistenceAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // ON CONFLICT DO NOTHING without a target skips the row on ANY unique conflict (here: (channel, external_id)
    // or (user_id, channel)), and the affected-row count tells which case happened: 1 stored, 0 conflict. Doing it in
    // one statement avoids "check, then insert", where two concurrent requests could both pass the check. A missing
    // user is not a unique conflict, so the foreign key still raises an error.
    @Override
    public boolean saveIfAvailable(ChannelIdentity identity) {
        int inserted = jdbcClient
                .sql("INSERT INTO channel_identity (id, user_id, channel, external_id) "
                        + "VALUES (:id, :userId, :channel, :externalId) ON CONFLICT DO NOTHING")
                .param("id", identity.getId())
                .param("userId", identity.getUserId())
                .param("channel", identity.getChannel().name())
                .param("externalId", identity.getExternalId())
                .update();
        return inserted == 1;
    }
}
