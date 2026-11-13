package com.expensebot.channels.infrastructure.telegram;

import com.expensebot.accounts.Channel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class TelegramReplySenderTest {
    @Mock
    private TelegramApi telegramApi;

    @Test
    void deliversToTheTelegramChannel() {
        assertThat(new TelegramReplySender(telegramApi).channel()).isEqualTo(Channel.TELEGRAM);
    }

    @Test
    void sendsTheTextToTheChatOfTheUser() {
        new TelegramReplySender(telegramApi).send("42", "Recibido");

        verify(telegramApi).sendMessage(42L, "Recibido");
    }

    @Test
    void rejectsAnExternalUserIdThatIsNotATelegramId() {
        var sender = new TelegramReplySender(telegramApi);

        assertThatThrownBy(() -> sender.send("not-a-number", "Recibido"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(telegramApi);
    }
}
