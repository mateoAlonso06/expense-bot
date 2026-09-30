package com.expensebot.expenses.infrastructure.persistence;

import com.expensebot.expenses.application.CategoryRepository;
import com.expensebot.expenses.domain.Category;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CategoryPersistenceAdapter implements CategoryRepository {
    private final CategoryJpaRepository categoryJpaRepository;

    public CategoryPersistenceAdapter(CategoryJpaRepository categoryJpaRepository) {
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Override
    public List<Category> findAll() {
        return categoryJpaRepository.findAll()
                .stream()
                .map(entity -> new Category(entity.getId(), entity.getName(), entity.isSystem()))
                .toList();
    }
}
