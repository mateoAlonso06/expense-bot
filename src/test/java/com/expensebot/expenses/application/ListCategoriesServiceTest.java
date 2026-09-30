package com.expensebot.expenses.application;

import com.expensebot.expenses.CategoryView;
import com.expensebot.expenses.domain.Category;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ListCategoriesServiceTest {
    @Mock
    private CategoryRepository categoryRepository;
    @InjectMocks
    private ListCategoriesService service;

    @Test
    void returnsAllCategories() {
        var firstCategory = new Category(UUID.randomUUID(), "Food", false);
        var secondCategory = new Category(UUID.randomUUID(), "Transport", false);

        when(categoryRepository.findAll()).thenReturn(List.of(
                firstCategory,
                secondCategory
        ));

        var result = service.execute();

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).extracting(CategoryView::name).isEqualTo("Food");
        assertThat(result.get(1)).extracting(CategoryView::name).isEqualTo("Transport");
    }

    @Test
    void returnsEmptyCategoriesListWhenNoCategoriesExist() {
        when(categoryRepository.findAll()).thenReturn(List.of());

        var result = service.execute();

        assertThat(result).isEmpty();
    }
}
