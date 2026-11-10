package com.expensebot.channels.infrastructure.telegram.polling;

import com.expensebot.channels.infrastructure.telegram.TelegramApi;
import com.expensebot.channels.infrastructure.telegram.TelegramUpdate;
import com.expensebot.channels.infrastructure.telegram.TelegramUpdateMapper;
import com.expensebot.channels.application.IncomingMessageGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** One round of long polling: fetch the pending updates and hand each one to the conversation. */
@Slf4j
@Component
public class TelegramPoller {
    static final int TIMEOUT_SECONDS = 30;

    private final TelegramApi telegramApi;
    private final TelegramUpdateMapper mapper;
    private final IncomingMessageGateway gateway;

    public TelegramPoller(TelegramApi telegramApi,
                          TelegramUpdateMapper mapper,
                          IncomingMessageGateway gateway) {
        this.telegramApi = telegramApi;
        this.mapper = mapper;
        this.gateway = gateway;
    }

    /** Telegram refuses {@code getUpdates} while a webhook is set, so it is removed before the first round. */
    public void prepare() {
        telegramApi.deleteWebhook();
    }

    /** Returns the offset for the next round: the highest update id seen plus one. */
    public long poll(long offset) {
        long nextOffset = offset;
        for (TelegramUpdate update : telegramApi.getUpdates(offset, TIMEOUT_SECONDS)) {
            handle(update);
            nextOffset = Math.max(nextOffset, update.updateId() + 1);
        }
        return nextOffset;
    }

    /**
     * A failing update is logged and skipped: if the offset did not move past it, Telegram would redeliver
     * it forever and block every message behind it.
     */
    private void handle(TelegramUpdate update) {
        try {
            mapper.toIncomingMessage(update).ifPresent(gateway::receive);
        } catch (RuntimeException e) {
            log.error("Failed to handle Telegram update {}", update.updateId(), e);
        }
    }
}
