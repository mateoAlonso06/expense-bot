package com.expensebot.expenses.infrastructure.persistence;

import com.expensebot.expenses.domain.Category;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(CategoryPersistenceAdapter.class)
@Tag("persistence")
class CategoryPersistenceAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    private CategoryPersistenceAdapter adapter;

    @Test
    void returnsTheSeededBaseCatalog() {
        List<Category> categories = adapter.findAll();

        assertThat(categories).hasSize(7);
    }

    @Test
    void seedsOtherAsTheOnlySystemCategoryWithItsFixedId() {
        List<Category> systemCategories = adapter.findAll().stream()
                .filter(Category::isSystem)
                .toList();

        assertThat(systemCategories).singleElement().satisfies(other -> {
            assertThat(other.getId()).isEqualTo(Category.OTHER_ID);
            assertThat(other.getName()).isEqualTo("Otros");
        });
    }
}
