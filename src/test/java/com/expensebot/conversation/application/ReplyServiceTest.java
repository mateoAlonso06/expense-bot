package com.expensebot.conversation.application;

import com.expensebot.accounts.Channel;
import com.expensebot.conversation.ReplySender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplyServiceTest {
    @Mock
    private ReplySender telegramSender;
    @Mock
    private ReplySender whatsappSender;

    private ReplyService serviceWithBothSenders() {
        when(telegramSender.channel()).thenReturn(Channel.TELEGRAM);
        when(whatsappSender.channel()).thenReturn(Channel.WHATSAPP);
        return new ReplyService(List.of(telegramSender, whatsappSender));
    }

    @Test
    void sendsThroughTheSenderOfTheChannel() {
        var service = serviceWithBothSenders();

        service.reply(Channel.TELEGRAM, "42", "Recibido");

        verify(telegramSender).send("42", "Recibido");
        verify(whatsappSender, never()).send(anyString(), anyString());
    }

    @Test
    void logsAndSwallowsASendingFailure() {
        var service = serviceWithBothSenders();
        doThrow(new IllegalStateException("Telegram is down")).when(telegramSender).send("42", "Recibido");

        assertThatCode(() -> service.reply(Channel.TELEGRAM, "42", "Recibido")).doesNotThrowAnyException();
    }

    @Test
    void doesNotFailWhenTheChannelHasNoSender() {
        when(telegramSender.channel()).thenReturn(Channel.TELEGRAM);
        var service = new ReplyService(List.of(telegramSender));

        assertThatCode(() -> service.reply(Channel.WHATSAPP, "42", "Recibido")).doesNotThrowAnyException();
        verify(telegramSender, never()).send(anyString(), anyString());
    }

    @Test
    void failsAtStartupWhenTwoSendersClaimTheSameChannel() {
        when(telegramSender.channel()).thenReturn(Channel.TELEGRAM);
        when(whatsappSender.channel()).thenReturn(Channel.TELEGRAM);

        assertThatThrownBy(() -> new ReplyService(List.of(telegramSender, whatsappSender)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingArguments() {
        var service = serviceWithBothSenders();

        assertThatThrownBy(() -> service.reply(null, "42", "Recibido")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.reply(Channel.TELEGRAM, null, "Recibido")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.reply(Channel.TELEGRAM, "42", null)).isInstanceOf(NullPointerException.class);
    }
}
