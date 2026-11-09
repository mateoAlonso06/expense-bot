package com.expensebot.channels.infrastructure.telegram.polling;

import com.expensebot.channels.infrastructure.telegram.TelegramApi;
import com.expensebot.channels.infrastructure.telegram.TelegramUpdate;
import com.expensebot.channels.infrastructure.telegram.TelegramUpdate.TelegramMessage;
import com.expensebot.channels.infrastructure.telegram.TelegramUpdate.TelegramUser;
import com.expensebot.channels.infrastructure.telegram.TelegramUpdateMapper;
import com.expensebot.conversation.HandleIncomingMessageUseCase;
import com.expensebot.conversation.IncomingMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramPollerTest {
    @Mock
    private TelegramApi telegramApi;
    @Mock
    private HandleIncomingMessageUseCase handleIncomingMessage;

    private TelegramPoller poller() {
        return new TelegramPoller(telegramApi, new TelegramUpdateMapper(), handleIncomingMessage);
    }

    private static TelegramUpdate textUpdate(long updateId, String text) {
        return new TelegramUpdate(updateId, new TelegramMessage(updateId, new TelegramUser(42), text, null));
    }

    @Test
    void handsEachMessageToTheConversationAndMovesTheOffsetPastTheLastOne() {
        when(telegramApi.getUpdates(10, TelegramPoller.TIMEOUT_SECONDS))
                .thenReturn(List.of(textUpdate(10, "uno"), textUpdate(11, "dos")));

        long next = poller().poll(10);

        var captor = ArgumentCaptor.forClass(IncomingMessage.class);
        verify(handleIncomingMessage, times(2)).execute(captor.capture());
        assertThat(captor.getAllValues()).extracting(IncomingMessage::text).containsExactly("uno", "dos");
        assertThat(next).isEqualTo(12);
    }

    @Test
    void keepsTheOffsetWhenNothingArrived() {
        when(telegramApi.getUpdates(10, TelegramPoller.TIMEOUT_SECONDS)).thenReturn(List.of());

        assertThat(poller().poll(10)).isEqualTo(10);
        verifyNoInteractions(handleIncomingMessage);
    }

    @Test
    void confirmsAnUpdateItIgnoresSoItIsNotDeliveredAgain() {
        var sticker = new TelegramUpdate(20, new TelegramMessage(1, new TelegramUser(42), null, null));
        when(telegramApi.getUpdates(0, TelegramPoller.TIMEOUT_SECONDS)).thenReturn(List.of(sticker));

        assertThat(poller().poll(0)).isEqualTo(21);
        verifyNoInteractions(handleIncomingMessage);
    }

    @Test
    void aFailingMessageDoesNotBlockTheOnesBehindIt() {
        when(telegramApi.getUpdates(0, TelegramPoller.TIMEOUT_SECONDS))
                .thenReturn(List.of(textUpdate(1, "falla"), textUpdate(2, "ok")));
        doThrow(new IllegalStateException("boom")).when(handleIncomingMessage)
                .execute(any(IncomingMessage.class));

        long next = poller().poll(0);

        verify(handleIncomingMessage, times(2)).execute(any(IncomingMessage.class));
        assertThat(next).isEqualTo(3);
    }
}
