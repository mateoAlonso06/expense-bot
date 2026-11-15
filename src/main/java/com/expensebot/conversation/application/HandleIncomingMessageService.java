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
 * Decides who is writing and routes the message: a link code links the sender; any other message is only accepted
 * from a linked account, and goes to {@link RegisterExpenseFromText}. Whatever happens, the user gets one reply on the
 * channel the message came from.
 */
@Slf4j
@Service
public class HandleIncomingMessageService implements HandleIncomingMessageUseCase {
    static final String LINKED = "Cuenta vinculada. Ya podés registrar tus gastos escribiéndome.";
    static final String INVALID_CODE = "Ese código no es válido o ya venció. Pedí uno nuevo en el panel web.";
    static final String CHANNEL_ALREADY_LINKED = "No pude vincular esta cuenta: ya está asociada a un usuario "
            + "o ya tenés otra cuenta de este canal vinculada.";
    static final String ALREADY_LINKED = "Esta cuenta ya está vinculada a tu usuario. Ya podés registrar tus gastos.";
    static final String NOT_LINKED = "Todavía no vinculaste tu cuenta. "
            + "Pedí un código en el panel web y envíamelo acá.";

    private final LinkChannelIdentityUseCase linkChannelIdentity;
    private final ResolveChannelIdentityUseCase resolveChannelIdentity;
    private final RegisterExpenseFromText registerExpenseFromText;
    private final ReplyService replyService;

    HandleIncomingMessageService(LinkChannelIdentityUseCase linkChannelIdentity,
                                 ResolveChannelIdentityUseCase resolveChannelIdentity,
                                 RegisterExpenseFromText registerExpenseFromText,
                                 ReplyService replyService) {
        this.linkChannelIdentity = linkChannelIdentity;
        this.resolveChannelIdentity = resolveChannelIdentity;
        this.registerExpenseFromText = registerExpenseFromText;
        this.replyService = replyService;
    }

    @Override
    public void execute(IncomingMessage message) {
        log.info("Received message channel={} eventId={} attachments={}",
                message.channel(), message.eventId(), message.attachments().size());
        try {
            route(message);
        } catch (RuntimeException e) {
            // Typically the database failing. The gateway already registered the message as received, so the
            // platform will not redeliver it: without this reply the message would be lost without the user knowing.
            log.error("Failed to handle message channel={} eventId={}", message.channel(), message.eventId(), e);
            reply(message, RegisterExpenseFromText.TRY_AGAIN);
        }
    }

    private void route(IncomingMessage message) {
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
        reply(message, registerExpenseFromText.replyTo(userId, message));
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
