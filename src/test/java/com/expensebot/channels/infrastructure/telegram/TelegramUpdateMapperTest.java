package com.expensebot.channels.infrastructure.telegram;

import com.expensebot.conversation.Channel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramUpdateMapperTest {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final TelegramUpdateMapper mapper = new TelegramUpdateMapper();

    private TelegramUpdate parse(String json) {
        return jsonMapper.readValue(json, TelegramUpdate.class);
    }

    @Test
    void mapsATextMessage() {
        var update = parse("""
                {"update_id": 9001,
                 "message": {"message_id": 5, "date": 1700000000,
                             "from": {"id": 42, "is_bot": false, "first_name": "Ana"},
                             "chat": {"id": 42, "type": "private"},
                             "text": "gaste 500 en el super"}}""");

        var result = mapper.toIncomingMessage(update);

        assertThat(result).hasValueSatisfying(message -> {
            assertThat(message.channel()).isEqualTo(Channel.TELEGRAM);
            assertThat(message.externalUserId()).isEqualTo("42");
            assertThat(message.eventId()).isEqualTo("9001");
            assertThat(message.text()).isEqualTo("gaste 500 en el super");
        });
    }

    @Test
    void usesTheCaptionAsTextForAPhoto() {
        var update = parse("""
                {"update_id": 9002,
                 "message": {"message_id": 6, "from": {"id": 42},
                             "photo": [{"file_id": "abc", "file_unique_id": "u", "width": 1, "height": 1}],
                             "caption": "ticket del super"}}""");

        assertThat(mapper.toIncomingMessage(update))
                .hasValueSatisfying(message -> assertThat(message.text()).isEqualTo("ticket del super"));
    }

    @Test
    void ignoresAnUpdateThatIsNotAMessage() {
        var update = parse("""
                {"update_id": 9003, "edited_message": {"message_id": 5, "from": {"id": 42}, "text": "x"}}""");

        assertThat(mapper.toIncomingMessage(update)).isEmpty();
    }

    @Test
    void ignoresAMessageWithoutTextOrCaption() {
        var update = parse("""
                {"update_id": 9004,
                 "message": {"message_id": 7, "from": {"id": 42}, "sticker": {"file_id": "s"}}}""");

        assertThat(mapper.toIncomingMessage(update)).isEmpty();
    }

    @Test
    void ignoresAMessageWithoutSender() {
        var update = parse("""
                {"update_id": 9005, "message": {"message_id": 8, "text": "hola"}}""");

        assertThat(mapper.toIncomingMessage(update)).isEmpty();
    }
}
