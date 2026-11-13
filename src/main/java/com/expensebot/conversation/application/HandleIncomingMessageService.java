package com.expensebot.conversation.application;

import com.expensebot.accounts.LinkChannelIdentityUseCase;
import com.expensebot.accounts.LinkResult;
import com.expensebot.accounts.ResolveChannelIdentityUseCase;
import com.expensebot.conversation.HandleIncomingMessageUseCase;
import com.expensebot.conversation.IncomingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * First decides who is writing: a message that is a link code links the sender; any other message is only
 * accepted from a linked account. Once the agent arrives (#10), the linked branch hands the message to it; until
 * then it acknowledges it with a fixed reply, so the whole loop can be tried with a real bot.
 */
@Slf4j
@Service
public class HandleIncomingMessageService implements HandleIncomingMessageUseCase {
    static final String ACKNOWLEDGEMENT = "Recibido";
    static final String LINKED = "Cuenta vinculada. Ya podés registrar tus gastos escribiéndome.";
    static final String INVALID_CODE = "Ese código no es válido o ya venció. Pedí uno nuevo en el panel web.";
    static final String CHANNEL_ALREADY_LINKED = "No pude vincular esta cuenta: ya está asociada a un usuario "
            + "o ya tenés otra cuenta de este canal vinculada.";
    static final String ALREADY_LINKED = "Esta cuenta ya está vinculada a tu usuario. Ya podés registrar tus gastos.";
    static final String NOT_LINKED = "Todavía no vinculaste tu cuenta. "
            + "Pedí un código en el panel web y envíamelo acá.";

    private final LinkChannelIdentityUseCase linkChannelIdentity;
    private final ResolveChannelIdentityUseCase resolveChannelIdentity;
    private final ReplyService replyService;

    public HandleIncomingMessageService(LinkChannelIdentityUseCase linkChannelIdentity,
                                        ResolveChannelIdentityUseCase resolveChannelIdentity,
                                        ReplyService replyService) {
        this.linkChannelIdentity = linkChannelIdentity;
        this.resolveChannelIdentity = resolveChannelIdentity;
        this.replyService = replyService;
    }

    @Override
    public void execute(IncomingMessage message) {
        log.info("Received message channel={} eventId={} attachments={}",
                message.channel(), message.eventId(), message.attachments().size());

        // The code is recognized deterministically, before anything else: it never reaches the LLM.
        LinkResult linkResult = linkChannelIdentity.execute(
                message.channel(), message.externalUserId(), message.text());
        if (linkResult != LinkResult.NOT_A_CODE) {
            reply(message, linkReply(linkResult));
            return;
        }

        UUID userId = resolveChannelIdentity.execute(message.channel(), message.externalUserId()).orElse(null);
        if (userId == null) {
            reply(message, NOT_LINKED);
            return;
        }
        reply(message, ACKNOWLEDGEMENT);
    }

    private static String linkReply(LinkResult result) {
        return switch (result) {
            case LINKED -> LINKED;
            case INVALID_CODE -> INVALID_CODE;
            case CHANNEL_ALREADY_LINKED -> CHANNEL_ALREADY_LINKED;
            case ALREADY_LINKED_TO_SAME_USER -> ALREADY_LINKED;
            case NOT_A_CODE -> throw new IllegalArgumentException("A regular message has no link reply");
        };
    }

    private void reply(IncomingMessage message, String text) {
        replyService.reply(message.channel(), message.externalUserId(), text);
    }
}
