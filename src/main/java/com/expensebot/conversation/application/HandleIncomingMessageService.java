package com.expensebot.conversation.application;

import com.expensebot.conversation.HandleIncomingMessageUseCase;
import com.expensebot.conversation.IncomingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Placeholder until the agent arrives (#10): it records that a message came in and acknowledges it with a fixed
 * reply, so the whole loop can be tried with a real bot.
 */
@Slf4j
@Service
public class HandleIncomingMessageService implements HandleIncomingMessageUseCase {
    static final String ACKNOWLEDGEMENT = "Recibido";

    private final ReplyService replyService;

    public HandleIncomingMessageService(ReplyService replyService) {
        this.replyService = replyService;
    }

    @Override
    public void execute(IncomingMessage message) {
        log.info("Received message channel={} eventId={} attachments={}",
                message.channel(), message.eventId(), message.attachments().size());
        replyService.reply(message.channel(), message.externalUserId(), ACKNOWLEDGEMENT);
    }
}
