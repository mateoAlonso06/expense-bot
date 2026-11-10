package com.expensebot.conversation.application;

import com.expensebot.accounts.Channel;
import com.expensebot.conversation.ReplySender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sends a reply through the sender of the channel the conversation came from. A failed send is logged and never
 * propagates, so a channel outage cannot undo what the conversation already did (for example a stored expense).
 */
@Slf4j
@Service
public class ReplyService {
    private final Map<Channel, ReplySender> sendersByChannel;

    /** Spring injects every {@link ReplySender} bean here. Two senders for one channel fail at startup. */
    public ReplyService(List<ReplySender> senders) {
        this.sendersByChannel = senders.stream()
                .collect(Collectors.toUnmodifiableMap(ReplySender::channel, Function.identity()));
    }

    public void reply(Channel channel, String externalUserId, String text) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(externalUserId, "externalUserId");
        Objects.requireNonNull(text, "text");
        ReplySender sender = sendersByChannel.get(channel);
        if (sender == null) {
            log.error("No reply sender for channel {}", channel);
            return;
        }
        try {
            sender.send(externalUserId, text);
        } catch (RuntimeException e) {
            log.error("Failed to send a reply on channel {}", channel, e);
        }
    }
}
