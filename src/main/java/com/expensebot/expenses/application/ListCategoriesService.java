package com.expensebot.expenses.application;

import com.expensebot.expenses.CategoryView;
import com.expensebot.expenses.ListCategoriesUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListCategoriesService implements ListCategoriesUseCase {
    private final CategoryRepository categoryRepository;

    public ListCategoriesService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryView> execute() {
        return categoryRepository.findAll()
                .stream()
                .map(category -> new CategoryView(category.getId(), category.getName()))
                .toList();
    }
}
