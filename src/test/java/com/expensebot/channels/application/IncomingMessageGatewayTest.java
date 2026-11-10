package com.expensebot.channels.application;

import com.expensebot.conversation.Channel;
import com.expensebot.conversation.HandleIncomingMessageUseCase;
import com.expensebot.conversation.IncomingMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncomingMessageGatewayTest {
    private static final IncomingMessage MESSAGE = new IncomingMessage(Channel.TELEGRAM, "42", "100", "hola", null);

    @Mock
    private ProcessedMessageRepository processedMessages;
    @Mock
    private HandleIncomingMessageUseCase handleIncomingMessage;
    @InjectMocks
    private IncomingMessageGateway gateway;

    @Test
    void handsTheFirstDeliveryToTheConversation() {
        when(processedMessages.registerIfNew(Channel.TELEGRAM, "100")).thenReturn(true);

        gateway.receive(MESSAGE);

        verify(handleIncomingMessage).execute(MESSAGE);
    }

    @Test
    void dropsARedeliveryWithoutTouchingTheConversation() {
        when(processedMessages.registerIfNew(Channel.TELEGRAM, "100")).thenReturn(false);

        gateway.receive(MESSAGE);

        verify(handleIncomingMessage, never()).execute(MESSAGE);
    }

    @Test
    void doesNotSwallowAFailureOfTheRegistry() {
        doThrow(new IllegalStateException("database is down")).when(processedMessages)
                .registerIfNew(Channel.TELEGRAM, "100");

        assertThatThrownBy(() -> gateway.receive(MESSAGE)).isInstanceOf(IllegalStateException.class);

        verify(handleIncomingMessage, never()).execute(MESSAGE);
    }
}
