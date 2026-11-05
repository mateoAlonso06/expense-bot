package com.expensebot.conversation;

import java.util.List;
import java.util.Objects;

/**
 * A message received from any channel, free of channel-specific types.
 *
 * @param eventId delivery id, unique across the whole channel (used to ignore redeliveries)
 * @param text    may be null when the message only carries attachments
 */
public record IncomingMessage(
        Channel channel,
        String externalUserId,
        String eventId,
        String text,
        List<Attachment> attachments) {

    public IncomingMessage {
        Objects.requireNonNull(channel, "Channel is required");
        Objects.requireNonNull(externalUserId, "External user id is required");
        Objects.requireNonNull(eventId, "Event id is required");
        if (externalUserId.isBlank() || eventId.isBlank()) {
            throw new IllegalArgumentException("External user id and event id must not be blank");
        }
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
        if ((text == null || text.isBlank()) && attachments.isEmpty()) {
            throw new IllegalArgumentException("A message needs text or at least one attachment");
        }
    }
}
