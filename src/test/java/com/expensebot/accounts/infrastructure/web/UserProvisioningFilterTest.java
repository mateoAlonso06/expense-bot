package com.expensebot.accounts.infrastructure.web;

import com.expensebot.accounts.ProvisionUserProfileUseCase;
import com.expensebot.config.SecurityConfig;
import com.expensebot.expenses.ListCategoriesUseCase;
import com.expensebot.expenses.infrastructure.web.CategoryController;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
@Tag("web")
class UserProvisioningFilterTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ProvisionUserProfileUseCase provisionUserProfile;
    @MockitoBean
    private ListCategoriesUseCase listCategoriesUseCase;
    // Required by the resource server config; jwt() below bypasses real token decoding.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void provisionsTheProfileFromSubjectAndEmailAndLetsTheRequestThrough() throws Exception {
        var sub = UUID.randomUUID();

        mockMvc.perform(get("/api/categories").with(token(t -> t.subject(sub.toString()).claim("email", "ana@example.com"))))
                .andExpect(status().isOk());

        verify(provisionUserProfile).execute(sub, "ana@example.com");
    }

    @Test
    void rejectsASubjectThatIsNotAUuid() throws Exception {
        mockMvc.perform(get("/api/categories").with(token(t -> t.subject("user-1").claim("email", "ana@example.com"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(provisionUserProfile);
    }

    @Test
    void rejectsALenientUuidThatJavaWouldAccept() throws Exception {
        mockMvc.perform(get("/api/categories").with(token(t -> t.subject("1-1-1-1-1").claim("email", "ana@example.com"))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(provisionUserProfile);
    }

    @Test
    void rejectsATokenWithoutEmail() throws Exception {
        mockMvc.perform(get("/api/categories").with(token(t -> t.subject(UUID.randomUUID().toString()))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(provisionUserProfile);
    }

    @Test
    void doesNothingForRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(provisionUserProfile);
    }

    private static RequestPostProcessor token(Consumer<org.springframework.security.oauth2.jwt.Jwt.Builder> claims) {
        return jwt().jwt(claims);
    }
}
