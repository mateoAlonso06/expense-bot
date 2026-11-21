package com.expensebot.conversation.infrastructure.persistence;

import com.expensebot.accounts.Channel;
import com.expensebot.conversation.application.PendingClarificationRepository;
import com.expensebot.conversation.domain.PendingClarification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PendingClarificationPersistenceAdapter implements PendingClarificationRepository {
    private final JdbcClient jdbcClient;

    public PendingClarificationPersistenceAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // One statement replaces the conversation's previous question: two messages arriving together cannot leave
    // two rows, and there is no "find, then insert or update" race.
    @Override
    public void save(PendingClarification clarification) {
        jdbcClient
                .sql("INSERT INTO pending_clarification (user_id, channel, original_message, description, "
                        + "category_name, amount_candidates, expires_at) "
                        + "VALUES (:userId, :channel, :originalMessage, :description, :categoryName, "
                        + ":amountCandidates, :expiresAt) "
                        + "ON CONFLICT (user_id, channel) DO UPDATE SET "
                        + "original_message = EXCLUDED.original_message, description = EXCLUDED.description, "
                        + "category_name = EXCLUDED.category_name, amount_candidates = EXCLUDED.amount_candidates, "
                        + "expires_at = EXCLUDED.expires_at, updated_at = now()")
                .param("userId", clarification.getUserId())
                .param("channel", clarification.getChannel().name())
                .param("originalMessage", clarification.getOriginalMessage())
                .param("description", clarification.getDescription())
                .param("categoryName", clarification.getCategoryName())
                .param("amountCandidates", clarification.getAmountCandidates().toArray(BigDecimal[]::new))
                .param("expiresAt", Timestamp.from(clarification.getExpiresAt()))
                .update();
    }

    @Override
    public Optional<PendingClarification> find(UUID userId, Channel channel) {
        return jdbcClient
                .sql("SELECT * FROM pending_clarification WHERE user_id = :userId AND channel = :channel")
                .param("userId", userId)
                .param("channel", channel.name())
                .query((rs, rowNum) -> restore(rs))
                .optional();
    }

    @Override
    public void delete(UUID userId, Channel channel) {
        jdbcClient
                .sql("DELETE FROM pending_clarification WHERE user_id = :userId AND channel = :channel")
                .param("userId", userId)
                .param("channel", channel.name())
                .update();
    }

    private static PendingClarification restore(ResultSet rs) throws SQLException {
        return PendingClarification.restore(
                rs.getObject("user_id", UUID.class),
                Channel.valueOf(rs.getString("channel")),
                rs.getString("original_message"),
                rs.getString("description"),
                rs.getString("category_name"),
                candidates(rs.getArray("amount_candidates")),
                rs.getTimestamp("expires_at").toInstant());
    }

    private static List<BigDecimal> candidates(Array array) throws SQLException {
        try {
            return List.of((BigDecimal[]) array.getArray());
        } finally {
            array.free();
        }
    }
}
