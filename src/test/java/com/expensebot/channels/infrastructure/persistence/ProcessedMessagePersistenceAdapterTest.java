package com.expensebot.channels.infrastructure.persistence;

import com.expensebot.accounts.Channel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
@Import(ProcessedMessagePersistenceAdapter.class)
@Tag("persistence")
class ProcessedMessagePersistenceAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private ProcessedMessagePersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registersTheFirstDeliveryOfAMessage() {
        var eventId = newEventId();

        assertThat(adapter.registerIfNew(Channel.TELEGRAM, eventId)).isTrue();

        assertThat(countOf(Channel.TELEGRAM, eventId)).isEqualTo(1);
    }

    @Test
    void rejectsTheSameMessageDeliveredAgain() {
        var eventId = newEventId();
        adapter.registerIfNew(Channel.TELEGRAM, eventId);

        assertThat(adapter.registerIfNew(Channel.TELEGRAM, eventId)).isFalse();

        assertThat(countOf(Channel.TELEGRAM, eventId)).isEqualTo(1);
    }

    @Test
    void treatsTheSameEventIdOnAnotherChannelAsADifferentMessage() {
        var eventId = newEventId();
        adapter.registerIfNew(Channel.TELEGRAM, eventId);

        assertThat(adapter.registerIfNew(Channel.WHATSAPP, eventId)).isTrue();
    }

    // Opts out of the transaction @DataJpaTest wraps around each test: every thread must commit
    // on its own, otherwise the database would never see the race. The adapter's single INSERT
    // runs in its own implicit transaction, so no TransactionTemplate is needed here.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void letsExactlyOneConcurrentDeliveryWin() throws Exception {
        var eventId = newEventId();
        int threads = 8;
        var startSignal = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int winners = 0;
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    startSignal.await();
                    return adapter.registerIfNew(Channel.TELEGRAM, eventId);
                }));
            }
            startSignal.countDown();
            for (Future<Boolean> result : results) {
                if (result.get()) { // rethrows if a thread failed (e.g. a duplicate key error)
                    winners++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(winners).isEqualTo(1);
        assertThat(countOf(Channel.TELEGRAM, eventId)).isEqualTo(1);
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }

    private int countOf(Channel channel, String eventId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM processed_message WHERE channel = ? AND event_id = ?",
                Integer.class, channel.name(), eventId);
    }
}
