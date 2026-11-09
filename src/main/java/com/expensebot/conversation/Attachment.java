package com.expensebot.conversation;

import java.util.Objects;

/** A file sent with a message, already downloaded by the channel adapter. */
public record Attachment(AttachmentType type, String mimeType, byte[] content) {
    public Attachment {
        Objects.requireNonNull(type, "Attachment type is required");
        Objects.requireNonNull(mimeType, "Attachment mime type is required");
        Objects.requireNonNull(content, "Attachment content is required");
    }
}
