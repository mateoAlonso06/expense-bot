package com.expensebot.conversation;

import com.expensebot.accounts.Channel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class IncomingMessageTest {

    @Test
    void acceptsATextMessage() {
        var message = new IncomingMessage(Channel.TELEGRAM, "42", "1001", "gaste 500 en el super", null);

        assertThat(message.text()).isEqualTo("gaste 500 en el super");
        assertThat(message.attachments()).isEmpty();
    }

    @Test
    void acceptsAnAttachmentWithoutText() {
        var photo = new Attachment(AttachmentType.IMAGE, "image/jpeg", new byte[]{1});

        var message = new IncomingMessage(Channel.TELEGRAM, "42", "1001", null, List.of(photo));

        assertThat(message.attachments()).containsExactly(photo);
    }

    @Test
    void rejectsAMessageWithNeitherTextNorAttachments() {
        assertThatThrownBy(() -> new IncomingMessage(Channel.TELEGRAM, "42", "1001", " ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankEventId() {
        assertThatThrownBy(() -> new IncomingMessage(Channel.TELEGRAM, "42", " ", "hola", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingChannel() {
        assertThatThrownBy(() -> new IncomingMessage(null, "42", "1001", "hola", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void attachmentsCannotBeModifiedAfterwards() {
        var message = new IncomingMessage(Channel.TELEGRAM, "42", "1001", "hola", null);

        assertThatThrownBy(() -> message.attachments().add(new Attachment(AttachmentType.AUDIO, "audio/ogg", new byte[0])))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
