package com.expensebot.accounts.infrastructure.persistence;

import com.expensebot.accounts.domain.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(UserProfilePersistenceAdapter.class)
class UserProfilePersistenceAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private UserProfilePersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void createsTheProfileWhenItDoesNotExist() {
        var id = UUID.randomUUID();

        adapter.createIfAbsent(UserProfile.create(id, "ana@example.com"));

        assertThat(emailOf(id)).isEqualTo("ana@example.com");
    }

    @Test
    void keepsTheExistingProfileWhenCalledAgainWithTheSameId() {
        var id = UUID.randomUUID();
        adapter.createIfAbsent(UserProfile.create(id, "ana@example.com"));

        adapter.createIfAbsent(UserProfile.create(id, "other@example.com"));

        assertThat(countOf(id)).isEqualTo(1);
        assertThat(emailOf(id)).isEqualTo("ana@example.com");
    }

    // Opts out of the transaction @DataJpaTest wraps around each test: every thread must commit
    // its own transaction, otherwise the database would never see the race.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void leavesExactlyOneProfileUnderConcurrentFirstRequests() throws Exception {
        var id = UUID.randomUUID();
        int threads = 8;
        var tx = new TransactionTemplate(transactionManager);
        var startSignal = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    startSignal.await();
                    tx.executeWithoutResult(status ->
                            adapter.createIfAbsent(UserProfile.create(id, "ana@example.com")));
                    return null;
                }));
            }
            startSignal.countDown();
            for (Future<?> result : results) {
                result.get(); // rethrows if any thread failed (e.g. a duplicate key error)
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(countOf(id)).isEqualTo(1);
    }

    private String emailOf(UUID id) {
        return jdbc.queryForObject("SELECT email FROM user_profile WHERE id = ?", String.class, id);
    }

    private int countOf(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM user_profile WHERE id = ?", Integer.class, id);
    }
}
