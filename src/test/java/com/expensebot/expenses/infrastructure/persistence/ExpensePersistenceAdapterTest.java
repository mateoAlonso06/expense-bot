package com.expensebot.expenses.infrastructure.persistence;

import com.expensebot.accounts.Channel;
import com.expensebot.expenses.domain.Category;
import com.expensebot.expenses.domain.Expense;
import com.expensebot.expenses.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ExpensePersistenceAdapter.class)
@Tag("persistence")
class ExpensePersistenceAdapterTest {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 13);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private ExpensePersistenceAdapter adapter;
    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void createTheUser() {
        userId = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profile (id, email) VALUES (?, ?)", userId, userId + "@example.com");
    }

    private Expense expense(UUID owner, UUID categoryId, String amount, String description) {
        var money = new Money(new BigDecimal(amount), Currency.getInstance("ARS"));
        return Expense.create(owner, categoryId, money, description, DAY, Channel.TELEGRAM, "$15.000 carnicería");
    }

    @Test
    void storesTheExpenseWithEveryField() {
        var expense = expense(userId, Category.OTHER_ID, "15000", "carnicería");

        adapter.save(expense);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM expense WHERE id = ?", expense.getId());
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("category_id")).isEqualTo(Category.OTHER_ID);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("15000.00");
        assertThat(row.get("currency")).isEqualTo("ARS");
        assertThat(row.get("description")).isEqualTo("carnicería");
        assertThat(((Date) row.get("spent_on")).toLocalDate()).isEqualTo(DAY);
        assertThat(row.get("source_channel")).isEqualTo("TELEGRAM");
        assertThat(row.get("original_message")).isEqualTo("$15.000 carnicería");
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void storesAnExpenseWithoutDescription() {
        var expense = expense(userId, Category.OTHER_ID, "100", null);

        adapter.save(expense);

        assertThat(jdbc.queryForObject("SELECT description FROM expense WHERE id = ?", String.class, expense.getId()))
                .isNull();
    }

    @Test
    void rejectsAnExpenseForAUserThatDoesNotExist() {
        assertThatThrownBy(() -> adapter.save(expense(UUID.randomUUID(), Category.OTHER_ID, "100", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAnExpenseWithACategoryThatDoesNotExist() {
        assertThatThrownBy(() -> adapter.save(expense(userId, UUID.randomUUID(), "100", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
