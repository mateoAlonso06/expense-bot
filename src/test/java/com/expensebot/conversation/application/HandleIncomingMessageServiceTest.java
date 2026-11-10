package com.expensebot.conversation.application;

import com.expensebot.accounts.Channel;
import com.expensebot.conversation.IncomingMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class HandleIncomingMessageServiceTest {
    @Mock
    private ReplyService replyService;

    @Test
    void acknowledgesTheMessageOnTheChannelItCameFrom() {
        var message = new IncomingMessage(Channel.TELEGRAM, "42", "100", "hola", null);

        new HandleIncomingMessageService(replyService).execute(message);

        verify(replyService).reply(Channel.TELEGRAM, "42", "Recibido");
    }
}
