package com.expensebot.expenses.infrastructure.web;

import com.expensebot.expenses.EditExpenseCommand;
import com.expensebot.expenses.EditExpenseUseCase;
import com.expensebot.expenses.RegisteredExpense;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {
    private final EditExpenseUseCase editExpense;

    public ExpenseController(EditExpenseUseCase editExpense) {
        this.editExpense = editExpense;
    }

    /** The owner comes from the token, never from the request: a user can only reach their own expenses. */
    @PatchMapping("/{id}")
    public RegisteredExpense edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                  @Valid @RequestBody EditExpenseRequest request) {
        // UserProvisioningFilter has already rejected any token whose subject is not a UUID.
        return editExpense.execute(new EditExpenseCommand(UUID.fromString(jwt.getSubject()), id,
                request.amount(), request.description(), request.categoryId()));
    }
}
