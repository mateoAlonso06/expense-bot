package com.expensebot.channels.infrastructure.telegram.web;

import com.expensebot.channels.infrastructure.telegram.TelegramUpdate;
import com.expensebot.channels.infrastructure.telegram.TelegramUpdateMapper;
import com.expensebot.conversation.HandleIncomingMessageUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Receives Telegram updates in production. Telegram has no JWT, so it authenticates with a shared secret. */
@RestController
@ConditionalOnProperty(name = "telegram.mode", havingValue = "webhook", matchIfMissing = true)
@RequestMapping(TelegramWebhookController.PATH)
public class TelegramWebhookController {
    static final String PATH = "/api/telegram/webhook";
    static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramUpdateMapper mapper;
    private final HandleIncomingMessageUseCase handleIncomingMessage;
    private final String secretToken;

    public TelegramWebhookController(TelegramUpdateMapper mapper,
                                     HandleIncomingMessageUseCase handleIncomingMessage,
                                     @Value("${telegram.webhook.secret-token:}") String secretToken) {
        this.mapper = mapper;
        this.handleIncomingMessage = handleIncomingMessage;
        this.secretToken = secretToken;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = SECRET_HEADER, required = false) String receivedToken,
                                        @RequestBody TelegramUpdate update) {
        if (!isValid(receivedToken)) {
            return ResponseEntity.status(401).build();
        }
        mapper.toIncomingMessage(update).ifPresent(handleIncomingMessage::execute);
        // Always 200 for an authentic update, even one we ignore, so Telegram stops redelivering it.
        return ResponseEntity.ok().build();
    }

    /** Fails closed: with no secret configured nothing is accepted. Constant-time comparison. */
    private boolean isValid(String receivedToken) {
        if (secretToken.isBlank() || receivedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                secretToken.getBytes(StandardCharsets.UTF_8),
                receivedToken.getBytes(StandardCharsets.UTF_8));
    }
}
