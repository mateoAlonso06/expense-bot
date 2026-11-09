package com.expensebot.channels.infrastructure.telegram.web;

import com.expensebot.accounts.ProvisionUserProfileUseCase;
import com.expensebot.channels.infrastructure.telegram.TelegramUpdateMapper;
import com.expensebot.config.SecurityConfig;
import com.expensebot.conversation.HandleIncomingMessageUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No secret configured: the endpoint must fail closed, even for a request with an empty header. */
@WebMvcTest(TelegramWebhookController.class)
@Import({SecurityConfig.class, TelegramUpdateMapper.class})
class TelegramWebhookWithoutSecretTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private HandleIncomingMessageUseCase handleIncomingMessage;
    @MockitoBean
    private ProvisionUserProfileUseCase provisionUserProfile;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsEverythingWhenNoSecretIsConfigured() throws Exception {
        mockMvc.perform(post("/api/telegram/webhook")
                        .header(TelegramWebhookController.SECRET_HEADER, "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"update_id\": 1}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(handleIncomingMessage);
    }
}
