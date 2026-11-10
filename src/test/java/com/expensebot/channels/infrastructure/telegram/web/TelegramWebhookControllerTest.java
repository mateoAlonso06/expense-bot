package com.expensebot.channels.infrastructure.telegram.web;

import com.expensebot.accounts.ProvisionUserProfileUseCase;
import com.expensebot.channels.infrastructure.telegram.TelegramUpdateMapper;
import com.expensebot.config.SecurityConfig;
import com.expensebot.accounts.Channel;
import com.expensebot.channels.application.IncomingMessageGateway;
import com.expensebot.conversation.IncomingMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = TelegramWebhookController.class, properties = "telegram.webhook.secret-token=test-secret")
@Import({SecurityConfig.class, TelegramUpdateMapper.class})
@Tag("web")
class TelegramWebhookControllerTest {
    private static final String URL = "/api/telegram/webhook";
    private static final String TEXT_UPDATE = """
            {"update_id": 9001, "message": {"message_id": 5, "from": {"id": 42}, "text": "gaste 500"}}""";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private IncomingMessageGateway gateway;
    // Required by SecurityConfig.
    @MockitoBean
    private ProvisionUserProfileUseCase provisionUserProfile;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void handsTheMessageToTheConversationWhenTheSecretMatches() throws Exception {
        mockMvc.perform(post(URL)
                        .header(TelegramWebhookController.SECRET_HEADER, "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEXT_UPDATE))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(IncomingMessage.class);
        verify(gateway).receive(captor.capture());
        assertThat(captor.getValue().channel()).isEqualTo(Channel.TELEGRAM);
        assertThat(captor.getValue().externalUserId()).isEqualTo("42");
        assertThat(captor.getValue().text()).isEqualTo("gaste 500");
    }

    @Test
    void rejectsAWrongSecret() throws Exception {
        mockMvc.perform(post(URL)
                        .header(TelegramWebhookController.SECRET_HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TEXT_UPDATE))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsARequestWithoutTheSecretHeader() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(TEXT_UPDATE))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(gateway);
    }

    @Test
    void acknowledgesAnUpdateItDoesNotHandle() throws Exception {
        mockMvc.perform(post(URL)
                        .header(TelegramWebhookController.SECRET_HEADER, "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"update_id\": 9002, \"edited_message\": {\"message_id\": 5}}"))
                .andExpect(status().isOk());

        verifyNoInteractions(gateway);
    }
}
