package com.expensebot.expenses.infrastructure.persistence;

import com.expensebot.expenses.application.CategoryRepository;
import com.expensebot.expenses.domain.Category;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
                .map(CategoryPersistenceAdapter::toDomain)
                .toList();
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return categoryJpaRepository.findById(id).map(CategoryPersistenceAdapter::toDomain);
    }

    private static Category toDomain(CategoryJpaEntity entity) {
        return Category.restore(entity.getId(), entity.getName(), entity.isSystem());
    }
}
