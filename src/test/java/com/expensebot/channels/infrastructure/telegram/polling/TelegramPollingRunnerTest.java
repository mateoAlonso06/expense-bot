package com.expensebot.channels.infrastructure.telegram.polling;

import com.expensebot.channels.infrastructure.telegram.TelegramApiException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class TelegramPollingRunnerTest {
    @Mock
    private TelegramPoller poller;

    @Test
    void keepsPollingWithTheOffsetTheLastRoundReturned() {
        when(poller.poll(0)).thenReturn(5L);
        when(poller.poll(5)).thenReturn(8L);
        when(poller.poll(8)).thenAnswer(invocation -> {
            Thread.sleep(Duration.ofSeconds(5));
            return 8L;
        });
        var runner = new TelegramPollingRunner(poller, Duration.ofMillis(10));

        runner.start();
        try {
            verify(poller, timeout(2000)).poll(8);
            assertThat(runner.isRunning()).isTrue();
        } finally {
            runner.stop();
        }
        assertThat(runner.isRunning()).isFalse();
    }

    @Test
    void preparesOnceBeforeTheFirstRound() {
        when(poller.poll(0)).thenReturn(5L);
        when(poller.poll(5)).thenAnswer(invocation -> {
            Thread.sleep(Duration.ofSeconds(5));
            return 5L;
        });
        var runner = new TelegramPollingRunner(poller, Duration.ofMillis(10));

        runner.start();
        try {
            verify(poller, timeout(2000)).poll(5);
        } finally {
            runner.stop();
        }
        var order = inOrder(poller);
        order.verify(poller).prepare();
        order.verify(poller).poll(0);
        verify(poller, times(1)).prepare();
    }

    @Test
    void retriesPreparingWhenTheWebhookCannotBeRemoved() {
        doThrow(new TelegramApiException("deleteWebhook failed: ResourceAccessException"))
                .doNothing()
                .when(poller).prepare();
        when(poller.poll(0)).thenAnswer(invocation -> {
            Thread.sleep(Duration.ofSeconds(5));
            return 0L;
        });
        var runner = new TelegramPollingRunner(poller, Duration.ofMillis(10));

        runner.start();
        try {
            verify(poller, timeout(2000)).poll(0);
        } finally {
            runner.stop();
        }
        verify(poller, times(2)).prepare();
    }

    @Test
    void retriesAfterAFailedRound() {
        when(poller.poll(0))
                .thenThrow(new TelegramApiException("getUpdates failed: ResourceAccessException"))
                .thenReturn(3L);
        when(poller.poll(3)).thenAnswer(invocation -> {
            Thread.sleep(Duration.ofSeconds(5));
            return 3L;
        });
        var runner = new TelegramPollingRunner(poller, Duration.ofMillis(10));

        runner.start();
        try {
            verify(poller, timeout(2000)).poll(3);
        } finally {
            runner.stop();
        }
    }
}
