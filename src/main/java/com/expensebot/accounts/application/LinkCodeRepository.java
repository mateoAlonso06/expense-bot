package com.expensebot.accounts.application;

import com.expensebot.accounts.domain.LinkCode;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LinkCodeRepository {
    void save(LinkCode linkCode);

    /**
     * Marks the code as used if it exists, is unused and has not expired at {@code now}. Must be atomic so that
     * concurrent attempts with the same code cannot both succeed.
     *
     * @return the id of the user who requested the code, or empty if it cannot be consumed
     */
    Optional<UUID> consume(String codeHash, Instant now);
}
