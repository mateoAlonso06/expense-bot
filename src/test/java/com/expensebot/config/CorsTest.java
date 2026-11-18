package com.expensebot.config;

import com.expensebot.accounts.ProvisionUserProfileUseCase;
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

import java.util.UUID;

import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The SPA runs on another origin than the API, so the browser only lets it read responses that carry CORS headers.
 * Allowed origins come from application.yaml (http://localhost:5173 by default).
 */
@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
@Tag("web")
class CorsTest {

    private static final String SPA_ORIGIN = "http://localhost:5173";
    private static final String OTHER_ORIGIN = "http://evil.example";

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ListCategoriesUseCase listCategoriesUseCase;
    // Required by SecurityConfig, which wires the provisioning filter.
    @MockitoBean
    private ProvisionUserProfileUseCase provisionUserProfile;
    // Required by the resource server config; jwt() below bypasses real token decoding.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void preflightFromAllowedOriginSucceedsWithoutToken() throws Exception {
        // The browser sends the preflight without the Authorization header, so it must not need a token.
        mockMvc.perform(options("/api/expenses/{id}", UUID.randomUUID())
                        .header(ORIGIN, SPA_ORIGIN)
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, SPA_ORIGIN));
    }

    @Test
    void authenticatedRequestFromAllowedOriginCarriesCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .header(ORIGIN, SPA_ORIGIN)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("email", "ana@example.com"))))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, SPA_ORIGIN));
    }

    @Test
    void unauthorizedResponseFromAllowedOriginCarriesCorsHeaders() throws Exception {
        // Lets the SPA read the 401 (and send the user to log in) instead of seeing an opaque network error.
        mockMvc.perform(get("/api/categories").header(ORIGIN, SPA_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, SPA_ORIGIN));
    }

    @Test
    void preflightFromOtherOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/categories")
                        .header(ORIGIN, OTHER_ORIGIN)
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void requestFromOtherOriginGetsNoCorsHeaders() throws Exception {
        mockMvc.perform(get("/api/categories")
                        .header(ORIGIN, OTHER_ORIGIN)
                        .with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("email", "ana@example.com"))))
                .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
