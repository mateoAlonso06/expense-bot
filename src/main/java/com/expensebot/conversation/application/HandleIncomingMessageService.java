package com.expensebot.conversation.application;

import com.expensebot.conversation.HandleIncomingMessageUseCase;
import com.expensebot.conversation.IncomingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Placeholder until the agent arrives (#10): it only records that a message came in. */
@Slf4j
@Service
public class HandleIncomingMessageService implements HandleIncomingMessageUseCase {

    @Override
    public void execute(IncomingMessage message) {
        log.info("Received message channel={} eventId={} attachments={}",
                message.channel(), message.eventId(), message.attachments().size());
    }
}
