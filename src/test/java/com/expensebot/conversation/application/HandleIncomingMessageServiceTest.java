package com.expensebot.conversation.application;

import com.expensebot.accounts.Channel;
import com.expensebot.accounts.LinkChannelIdentityUseCase;
import com.expensebot.accounts.LinkResult;
import com.expensebot.accounts.ResolveChannelIdentityUseCase;
import com.expensebot.conversation.Attachment;
import com.expensebot.conversation.AttachmentType;
import com.expensebot.conversation.IncomingMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Only the routing: who is writing and where the message goes. What happens to an expense is tested elsewhere. */
@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class HandleIncomingMessageServiceTest {
    @Mock
    private LinkChannelIdentityUseCase linkChannelIdentity;
    @Mock
    private ResolveChannelIdentityUseCase resolveChannelIdentity;
    @Mock
    private RegisterExpenseFromText registerExpenseFromText;
    @Mock
    private ReplyService replyService;
    @InjectMocks
    private HandleIncomingMessageService service;

    private static IncomingMessage telegram(String text) {
        return new IncomingMessage(Channel.TELEGRAM, "42", "100", text, null);
    }

    @Test
    void confirmsALinkedAccountAndDoesNotLookTheUserUp() {
        when(linkChannelIdentity.execute(Channel.TELEGRAM, "42", "K7M4-9QXT")).thenReturn(LinkResult.LINKED);

        service.execute(telegram("K7M4-9QXT"));

        verify(replyService).reply(Channel.TELEGRAM, "42", HandleIncomingMessageService.LINKED);
        verifyNoInteractions(resolveChannelIdentity, registerExpenseFromText);
    }

    @Test
    void tellsTheUserWhenTheCodeIsInvalid() {
        when(linkChannelIdentity.execute(Channel.TELEGRAM, "42", "K7M4-9QXT")).thenReturn(LinkResult.INVALID_CODE);

        service.execute(telegram("K7M4-9QXT"));

        verify(replyService).reply(Channel.TELEGRAM, "42", HandleIncomingMessageService.INVALID_CODE);
    }

    @Test
    void tellsTheUserWhenTheChannelAccountCannotBeLinked() {
        when(linkChannelIdentity.execute(Channel.TELEGRAM, "42", "K7M4-9QXT"))
                .thenReturn(LinkResult.CHANNEL_ALREADY_LINKED);

        service.execute(telegram("K7M4-9QXT"));

        verify(replyService).reply(Channel.TELEGRAM, "42", HandleIncomingMessageService.CHANNEL_ALREADY_LINKED);
    }

    @Test
    void tellsTheUserWhenTheAccountIsAlreadyLinkedToThem() {
        when(linkChannelIdentity.execute(Channel.TELEGRAM, "42", "K7M4-9QXT"))
                .thenReturn(LinkResult.ALREADY_LINKED_TO_SAME_USER);

        service.execute(telegram("K7M4-9QXT"));

        verify(replyService).reply(Channel.TELEGRAM, "42", HandleIncomingMessageService.ALREADY_LINKED);
    }

    @Test
    void asksAnUnlinkedUserToLinkTheAccount() {
        when(linkChannelIdentity.execute(Channel.TELEGRAM, "42", "gasté 500")).thenReturn(LinkResult.NOT_A_CODE);
        when(resolveChannelIdentity.execute(Channel.TELEGRAM, "42")).thenReturn(Optional.empty());

        service.execute(telegram("gasté 500"));

        verify(replyService).reply(Channel.TELEGRAM, "42", HandleIncomingMessageService.NOT_LINKED);
        verifyNoInteractions(registerExpenseFromText);
    }

    @Test
    void sendsTheMessageOfALinkedUserToTheExpenseFlowAndRepliesWithItsAnswer() {
        UUID userId = UUID.randomUUID();
        IncomingMessage message = telegram("gasté 500");
        when(linkChannelIdentity.execute(Channel.TELEGRAM, "42", "gasté 500")).thenReturn(LinkResult.NOT_A_CODE);
        when(resolveChannelIdentity.execute(Channel.TELEGRAM, "42")).thenReturn(Optional.of(userId));
        when(registerExpenseFromText.replyTo(userId, message)).thenReturn("Registré $500,00 en Otros (14/11/2026).");

        service.execute(message);

        verify(replyService).reply(Channel.TELEGRAM, "42", "Registré $500,00 en Otros (14/11/2026).");
    }

    @Test
    void tellsTheUserToTryAgainWhenSomethingFailsInsteadOfLosingTheMessageSilently() {
        when(linkChannelIdentity.execute(Channel.TELEGRAM, "42", "gasté 500")).thenReturn(LinkResult.NOT_A_CODE);
        when(resolveChannelIdentity.execute(Channel.TELEGRAM, "42"))
                .thenThrow(new IllegalStateException("database unavailable"));

        service.execute(telegram("gasté 500"));

        verify(replyService).reply(Channel.TELEGRAM, "42", RegisterExpenseFromText.TRY_AGAIN);
        verifyNoInteractions(registerExpenseFromText);
    }

    @Test
    void asksAnUnlinkedUserToLinkEvenWhenTheMessageOnlyCarriesAnAttachment() {
        var photo = new Attachment(AttachmentType.IMAGE, "image/jpeg", new byte[]{1});
        var message = new IncomingMessage(Channel.TELEGRAM, "42", "101", null, List.of(photo));
        when(linkChannelIdentity.execute(Channel.TELEGRAM, "42", null)).thenReturn(LinkResult.NOT_A_CODE);
        when(resolveChannelIdentity.execute(Channel.TELEGRAM, "42")).thenReturn(Optional.empty());

        service.execute(message);

        verify(replyService).reply(Channel.TELEGRAM, "42", HandleIncomingMessageService.NOT_LINKED);
        verifyNoInteractions(registerExpenseFromText);
    }
}
