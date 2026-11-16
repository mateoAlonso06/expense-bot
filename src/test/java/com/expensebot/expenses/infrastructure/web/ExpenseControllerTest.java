package com.expensebot.expenses.infrastructure.web;

import com.expensebot.accounts.ProvisionUserProfileUseCase;
import com.expensebot.config.SecurityConfig;
import com.expensebot.expenses.EditExpenseCommand;
import com.expensebot.expenses.EditExpenseUseCase;
import com.expensebot.expenses.ExpenseNotFoundException;
import com.expensebot.expenses.RegisteredExpense;
import com.expensebot.expenses.UnknownCategoryException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@Import(SecurityConfig.class)
@Tag("web")
class ExpenseControllerTest {
    private static final UUID USER = UUID.randomUUID();
    private static final UUID EXPENSE = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EditExpenseUseCase editExpense;
    // Required by SecurityConfig, which wires the provisioning filter.
    @MockitoBean
    private ProvisionUserProfileUseCase provisionUserProfile;
    // Required by the resource server config; jwt() below bypasses real token decoding.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private ResultActions patchExpense(String body) throws Exception {
        return mockMvc.perform(patch("/api/expenses/{id}", EXPENSE)
                .with(jwt().jwt(token -> token.subject(USER.toString()).claim("email", "ana@example.com")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void editsTheExpenseOfTheUserInTheToken() throws Exception {
        when(editExpense.execute(any())).thenReturn(new RegisteredExpense(EXPENSE, new BigDecimal("12500.00"),
                "ARS", "carnicería", "Alimentación", LocalDate.of(2026, 11, 13)));

        patchExpense("{\"amount\": 12500}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EXPENSE.toString()))
                .andExpect(jsonPath("$.amount").value(12500.00))
                .andExpect(jsonPath("$.categoryName").value("Alimentación"))
                .andExpect(jsonPath("$.spentOn").value("2026-11-13"));
        verify(editExpense).execute(new EditExpenseCommand(USER, EXPENSE, new BigDecimal("12500"), null, null));
    }

    @Test
    void answersNotFoundForAnotherUsersOrAnUnknownExpense() throws Exception {
        when(editExpense.execute(any())).thenThrow(new ExpenseNotFoundException(EXPENSE));

        patchExpense("{\"amount\": 100}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Expense not found: " + EXPENSE));
    }

    @Test
    void rejectsAnUnknownCategory() throws Exception {
        var category = UUID.randomUUID();
        when(editExpense.execute(any())).thenThrow(new UnknownCategoryException(category));

        patchExpense("{\"categoryId\": \"" + category + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown category: " + category));
    }

    @Test
    void rejectsAnAmountThatIsNotPositiveBeforeReachingTheUseCase() throws Exception {
        patchExpense("{\"amount\": 0}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        patchExpense("{\"amount\": -5}").andExpect(status().isBadRequest());
        verifyNoInteractions(editExpense);
    }

    @Test
    void rejectsAnAmountWithMoreThanTwoDecimals() throws Exception {
        patchExpense("{\"amount\": 10.123}").andExpect(status().isBadRequest());
        verifyNoInteractions(editExpense);
    }

    @Test
    void rejectsADescriptionLongerThanTheColumn() throws Exception {
        patchExpense("{\"description\": \"" + "x".repeat(256) + "\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(editExpense);
    }

    @Test
    void rejectsAnAttemptToChangeAnImmutableField() throws Exception {
        patchExpense("{\"spentOn\": \"2020-01-01\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        patchExpense("{\"amount\": 100, \"sourceChannel\": \"WHATSAPP\"}").andExpect(status().isBadRequest());
        patchExpense("{\"originalMessage\": \"otro\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(editExpense);
    }

    @Test
    void requiresAToken() throws Exception {
        mockMvc.perform(patch("/api/expenses/{id}", EXPENSE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(editExpense);
    }
}
