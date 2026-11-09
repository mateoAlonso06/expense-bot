package com.expensebot.channels.infrastructure.telegram.polling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Keeps polling Telegram on a background thread while the application runs (dev mode). */
@Slf4j
@Component
@ConditionalOnProperty(name = "telegram.mode", havingValue = "polling")
public class TelegramPollingRunner implements SmartLifecycle {
    private final TelegramPoller poller;
    private final Duration retryDelay;
    private volatile boolean running;
    private Thread thread;

    public TelegramPollingRunner(TelegramPoller poller,
                                 @Value("${telegram.polling.retry-delay:5s}") Duration retryDelay) {
        this.poller = poller;
        this.retryDelay = retryDelay;
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofPlatform().name("telegram-polling").daemon().start(this::loop);
        log.info("Telegram long polling started");
    }

    @Override
    public void stop() {
        running = false;
        thread.interrupt();
        log.info("Telegram long polling stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        long offset = 0;
        boolean prepared = false;
        while (running) {
            try {
                if (!prepared) {
                    poller.prepare();
                    prepared = true;
                }
                offset = poller.poll(offset);
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
                log.warn("Telegram polling failed, retrying in {}: {}", retryDelay, e.getMessage());
                if (!sleepBeforeRetry()) {
                    return;
                }
            }
        }
    }

    /** False when the thread was interrupted, which means the application is shutting down. */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(retryDelay);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
