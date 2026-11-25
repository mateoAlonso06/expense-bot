package com.expensebot.expenses.application;

import com.expensebot.accounts.Channel;
import com.expensebot.expenses.RegisteredExpense;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class ListRecentExpensesServiceTest {
    private static final Instant SINCE = Instant.parse("2026-11-25T11:30:00Z");

    @Mock
    private ExpenseRepository expenseRepository;
    @InjectMocks
    private ListRecentExpensesService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void returnsTheRecentExpensesOfTheUserOnThatChannel() {
        var expense = new RegisteredExpense(UUID.randomUUID(), new BigDecimal("15000"), "ARS", "carnicería",
                "Alimentación", LocalDate.of(2026, 11, 25));
        when(expenseRepository.findRecent(userId, Channel.TELEGRAM, SINCE)).thenReturn(List.of(expense));

        assertThat(service.execute(userId, Channel.TELEGRAM, SINCE)).containsExactly(expense);
    }

    @Test
    void requiresTheUserTheChannelAndTheInstant() {
        assertThatThrownBy(() -> service.execute(null, Channel.TELEGRAM, SINCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.execute(userId, null, SINCE)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> service.execute(userId, Channel.TELEGRAM, null))
                .isInstanceOf(NullPointerException.class);
    }
}
