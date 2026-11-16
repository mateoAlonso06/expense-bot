package com.expensebot.expenses.application;

import com.expensebot.accounts.Channel;
import com.expensebot.expenses.RegisterExpenseCommand;
import com.expensebot.expenses.RegisteredExpense;
import com.expensebot.expenses.domain.Category;
import com.expensebot.expenses.domain.Expense;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("mocks")
class RegisterExpenseServiceTest {
    // 01:30 UTC on the 14th is still 22:30 on the 13th in Buenos Aires (UTC-3).
    private static final Instant NOW = Instant.parse("2026-11-14T01:30:00Z");
    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Category OTHER = Category.restore(Category.OTHER_ID, "Otros", true);
    private static final Category FOOD = Category.restore(UUID.randomUUID(), "Alimentación", false);

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private CategoryRepository categoryRepository;

    private RegisterExpenseService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void createTheService() {
        service = new RegisterExpenseService(expenseRepository, categoryRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), BUENOS_AIRES, "ARS");
    }

    private RegisterExpenseCommand command(String amount, UUID categoryId, LocalDate spentOn) {
        return new RegisterExpenseCommand(userId, new BigDecimal(amount), "carnicería", categoryId, spentOn,
                Channel.TELEGRAM, "$15.000 carnicería");
    }

    private Expense stored() {
        var captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void storesTheExpenseUnderOtherWhenThereIsNoCategory() {
        when(categoryRepository.findById(Category.OTHER_ID)).thenReturn(Optional.of(OTHER));

        service.execute(command("15000", null, null));

        assertThat(stored().getCategoryId()).isEqualTo(Category.OTHER_ID);
    }

    @Test
    void storesTheExpenseUnderTheGivenCategory() {
        when(categoryRepository.findById(FOOD.getId())).thenReturn(Optional.of(FOOD));

        service.execute(command("15000", FOOD.getId(), null));

        assertThat(stored().getCategoryId()).isEqualTo(FOOD.getId());
    }

    @Test
    void defaultsTheDateToTodayInTheConfiguredZoneNotInUtc() {
        when(categoryRepository.findById(Category.OTHER_ID)).thenReturn(Optional.of(OTHER));

        service.execute(command("15000", null, null));

        assertThat(stored().getSpentOn()).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    void keepsTheDateWhenOneIsGiven() {
        when(categoryRepository.findById(Category.OTHER_ID)).thenReturn(Optional.of(OTHER));

        service.execute(command("15000", null, LocalDate.of(2026, 11, 1)));

        assertThat(stored().getSpentOn()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void storesTheExpenseInTheConfiguredCurrencyForTheUserAndChannel() {
        when(categoryRepository.findById(Category.OTHER_ID)).thenReturn(Optional.of(OTHER));

        service.execute(command("15000", null, null));

        Expense expense = stored();
        assertThat(expense.getMoney().currency().getCurrencyCode()).isEqualTo("ARS");
        assertThat(expense.getMoney().amount()).isEqualByComparingTo("15000");
        assertThat(expense.getUserId()).isEqualTo(userId);
        assertThat(expense.getSourceChannel()).isEqualTo(Channel.TELEGRAM);
        assertThat(expense.getOriginalMessage()).isEqualTo("$15.000 carnicería");
    }

    @Test
    void returnsTheSummaryTheBotNeeds() {
        when(categoryRepository.findById(FOOD.getId())).thenReturn(Optional.of(FOOD));

        RegisteredExpense result = service.execute(command("15000", FOOD.getId(), null));

        assertThat(result.id()).isEqualTo(stored().getId());
        assertThat(result.amount()).isEqualByComparingTo("15000");
        assertThat(result.currency()).isEqualTo("ARS");
        assertThat(result.description()).isEqualTo("carnicería");
        assertThat(result.categoryName()).isEqualTo("Alimentación");
        assertThat(result.spentOn()).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    void storesNothingWhenTheCategoryDoesNotExist() {
        var unknown = UUID.randomUUID();
        when(categoryRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(command("15000", unknown, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void storesNothingWhenTheAmountIsInvalid() {
        when(categoryRepository.findById(Category.OTHER_ID)).thenReturn(Optional.of(OTHER));

        assertThatThrownBy(() -> service.execute(command("0", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.execute(command("10.123", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void failsAtStartupWithAnUnknownCurrency() {
        assertThatThrownBy(() -> new RegisterExpenseService(expenseRepository, categoryRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), BUENOS_AIRES, "XYZ"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
