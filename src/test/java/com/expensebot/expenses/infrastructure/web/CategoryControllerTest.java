package com.expensebot.expenses.infrastructure.web;

import com.expensebot.accounts.ProvisionUserProfileUseCase;
import com.expensebot.config.SecurityConfig;
import com.expensebot.expenses.CategoryView;
import com.expensebot.expenses.ListCategoriesUseCase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import(SecurityConfig.class)
@Tag("web")
class CategoryControllerTest {

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
    void listsCategoriesAsJson() throws Exception {
        UUID id = UUID.randomUUID();
        when(listCategoriesUseCase.execute()).thenReturn(List.of(new CategoryView(id, "Alimentación")));

        mockMvc.perform(get("/api/categories").with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()).claim("email", "ana@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].name").value("Alimentación"));
    }
}
