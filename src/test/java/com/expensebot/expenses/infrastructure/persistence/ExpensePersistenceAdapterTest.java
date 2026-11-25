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
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant SINCE = NOW.minusSeconds(30 * 60);

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
        return expense(owner, categoryId, amount, description, Channel.TELEGRAM);
    }

    private Expense expense(UUID owner, UUID categoryId, String amount, String description, Channel channel) {
        var money = new Money(new BigDecimal(amount), Currency.getInstance("ARS"));
        return Expense.create(owner, categoryId, money, description, DAY, channel, "$15.000 carnicería");
    }

    /** Saves the expense as stored {@code minutesAgo} before {@link #NOW}: every row of a test transaction gets the
     * same now(), so the time is set by hand. */
    private Expense storedMinutesAgo(Expense expense, int minutesAgo) {
        adapter.save(expense);
        jdbc.update("UPDATE expense SET created_at = ? WHERE id = ?",
                OffsetDateTime.ofInstant(NOW.minusSeconds(minutesAgo * 60L), ZoneOffset.UTC), expense.getId());
        return expense;
    }

    private UUID anotherUser() {
        UUID other = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profile (id, email) VALUES (?, ?)", other, other + "@example.com");
        return other;
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

    @Test
    void findsAnExpenseOfItsOwnerWithEveryField() {
        var stored = expense(userId, Category.OTHER_ID, "15000", "carnicería");
        adapter.save(stored);

        var found = adapter.findByIdAndUserId(stored.getId(), userId);

        assertThat(found).hasValueSatisfying(expense -> {
            assertThat(expense.getId()).isEqualTo(stored.getId());
            assertThat(expense.getUserId()).isEqualTo(userId);
            assertThat(expense.getCategoryId()).isEqualTo(Category.OTHER_ID);
            assertThat(expense.getMoney()).isEqualTo(stored.getMoney());
            assertThat(expense.getDescription()).isEqualTo("carnicería");
            assertThat(expense.getSpentOn()).isEqualTo(DAY);
            assertThat(expense.getSourceChannel()).isEqualTo(Channel.TELEGRAM);
            assertThat(expense.getOriginalMessage()).isEqualTo("$15.000 carnicería");
        });
    }

    @Test
    void doesNotFindAnotherUsersExpenseNorAnUnknownOne() {
        var stored = expense(userId, Category.OTHER_ID, "100", null);
        adapter.save(stored);

        assertThat(adapter.findByIdAndUserId(stored.getId(), UUID.randomUUID())).isEmpty();
        assertThat(adapter.findByIdAndUserId(UUID.randomUUID(), userId)).isEmpty();
    }

    @Test
    void updatesOnlyTheEditableFields() {
        var stored = expense(userId, Category.OTHER_ID, "100", "café");
        adapter.save(stored);
        // now() is the start of the test transaction, so push updated_at back to see it move.
        jdbc.update("UPDATE expense SET updated_at = now() - interval '1 day' WHERE id = ?", stored.getId());
        UUID food = jdbc.queryForObject("SELECT id FROM category WHERE name = 'Alimentación'", UUID.class);
        var expense = adapter.findByIdAndUserId(stored.getId(), userId).orElseThrow();

        expense.changeAmount(new Money(new BigDecimal("250.50"), Currency.getInstance("ARS")));
        expense.changeDescription("medialunas");
        expense.changeCategory(food);
        adapter.update(expense);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM expense WHERE id = ?", stored.getId());
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("250.50");
        assertThat(row.get("description")).isEqualTo("medialunas");
        assertThat(row.get("category_id")).isEqualTo(food);
        assertThat(((Date) row.get("spent_on")).toLocalDate()).isEqualTo(DAY);
        assertThat(row.get("original_message")).isEqualTo("$15.000 carnicería");
        assertThat((Boolean) jdbc.queryForObject(
                "SELECT updated_at = now() FROM expense WHERE id = ?", Boolean.class, stored.getId())).isTrue();
    }

    @Test
    void failsToUpdateAnExpenseThatIsNotStored() {
        assertThatThrownBy(() -> adapter.update(expense(userId, Category.OTHER_ID, "100", null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findsTheRecentExpensesNewestFirstWithTheirCategoryName() {
        UUID food = jdbc.queryForObject("SELECT id FROM category WHERE name = 'Alimentación'", UUID.class);
        var older = storedMinutesAgo(expense(userId, Category.OTHER_ID, "3000", "uber"), 20);
        var newer = storedMinutesAgo(expense(userId, food, "15000", "carnicería"), 5);

        var recent = adapter.findRecent(userId, Channel.TELEGRAM, SINCE);

        assertThat(recent).extracting("id").containsExactly(newer.getId(), older.getId());
        assertThat(recent.getFirst()).satisfies(expense -> {
            assertThat(expense.amount()).isEqualByComparingTo("15000");
            assertThat(expense.currency()).isEqualTo("ARS");
            assertThat(expense.description()).isEqualTo("carnicería");
            assertThat(expense.categoryName()).isEqualTo("Alimentación");
            assertThat(expense.spentOn()).isEqualTo(DAY);
        });
    }

    @Test
    void recentIncludesTheFirstInstantAndNothingBefore() {
        var atTheEdge = storedMinutesAgo(expense(userId, Category.OTHER_ID, "100", "café"), 30);
        storedMinutesAgo(expense(userId, Category.OTHER_ID, "200", "kiosco"), 31);

        assertThat(adapter.findRecent(userId, Channel.TELEGRAM, SINCE)).extracting("id")
                .containsExactly(atTheEdge.getId());
    }

    @Test
    void recentLeavesOutOtherUsersAndOtherChannels() {
        var mine = storedMinutesAgo(expense(userId, Category.OTHER_ID, "100", "café"), 5);
        storedMinutesAgo(expense(anotherUser(), Category.OTHER_ID, "200", "kiosco"), 5);
        storedMinutesAgo(expense(userId, Category.OTHER_ID, "300", "nafta", Channel.WHATSAPP), 5);

        assertThat(adapter.findRecent(userId, Channel.TELEGRAM, SINCE)).extracting("id")
                .containsExactly(mine.getId());
    }

    @Test
    void recentIsEmptyWhenNothingWasStoredInTheWindow() {
        storedMinutesAgo(expense(userId, Category.OTHER_ID, "100", "café"), 60);

        assertThat(adapter.findRecent(userId, Channel.TELEGRAM, SINCE)).isEmpty();
    }
}
