package com.expensebot.accounts.infrastructure.persistence;

import com.expensebot.accounts.application.LinkCodeRepository;
import com.expensebot.accounts.domain.LinkCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public class LinkCodePersistenceAdapter implements LinkCodeRepository {
    private final JdbcClient jdbcClient;

    public LinkCodePersistenceAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    // Plain SQL on purpose: linking will consume a code with a single UPDATE ... WHERE used_at IS NULL, which
    // JPA cannot express atomically, so the whole table is handled the same way. created_at / updated_at use
    // the column defaults.
    @Override
    public void save(LinkCode linkCode) {
        jdbcClient
                .sql("INSERT INTO link_code (id, user_id, code_hash, expires_at) "
                        + "VALUES (:id, :userId, :codeHash, :expiresAt)")
                .param("id", linkCode.getId())
                .param("userId", linkCode.getUserId())
                .param("codeHash", linkCode.getCodeHash())
                .param("expiresAt", Timestamp.from(linkCode.getExpiresAt()))
                .update();
    }
}
