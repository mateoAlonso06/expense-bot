package com.expensebot.channels.application;

import com.expensebot.conversation.HandleIncomingMessageUseCase;
import com.expensebot.conversation.IncomingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The single door through which every channel adapter hands a message to the conversation. Platforms
 * redeliver, so a message is registered first and only the first delivery moves on; a duplicate is
 * dropped here and the adapter still acknowledges it, which is what makes the platform stop retrying.
 */
@Slf4j
@Service
public class IncomingMessageGateway {
    private final ProcessedMessageRepository processedMessages;
    private final HandleIncomingMessageUseCase handleIncomingMessage;

    public IncomingMessageGateway(ProcessedMessageRepository processedMessages,
                                  HandleIncomingMessageUseCase handleIncomingMessage) {
        this.processedMessages = processedMessages;
        this.handleIncomingMessage = handleIncomingMessage;
    }

    public void receive(IncomingMessage message) {
        if (!processedMessages.registerIfNew(message.channel(), message.eventId())) {
            log.info("Ignoring duplicate message channel={} eventId={}", message.channel(), message.eventId());
            return;
        }
        handleIncomingMessage.execute(message);
    }
}
