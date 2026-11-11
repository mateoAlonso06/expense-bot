package com.expensebot.accounts.infrastructure.web;

import com.expensebot.accounts.IssuedLinkCode;
import com.expensebot.accounts.ProvisionUserProfileUseCase;
import com.expensebot.accounts.RequestLinkCodeUseCase;
import com.expensebot.config.SecurityConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LinkCodeController.class)
@Import(SecurityConfig.class)
@Tag("web")
class LinkCodeControllerTest {
    private static final String URL = "/api/link-codes";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private RequestLinkCodeUseCase requestLinkCode;
    // Required by SecurityConfig, which wires the provisioning filter.
    @MockitoBean
    private ProvisionUserProfileUseCase provisionUserProfile;
    // Required by the resource server config; jwt() below bypasses real token decoding.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void issuesACodeForTheUserIdentifiedByTheTokenSubject() throws Exception {
        var userId = UUID.randomUUID();
        when(requestLinkCode.execute(userId))
                .thenReturn(new IssuedLinkCode("K7M4-9QXT", Instant.parse("2026-11-10T12:10:00Z")));

        mockMvc.perform(post(URL).with(jwt().jwt(token -> token
                        .subject(userId.toString()).claim("email", "ana@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("K7M4-9QXT"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-10T12:10:00Z"));
    }

    @Test
    void rejectsARequestWithoutAToken() throws Exception {
        mockMvc.perform(post(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(requestLinkCode);
    }
}
