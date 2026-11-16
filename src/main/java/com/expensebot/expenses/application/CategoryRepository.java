package com.expensebot.expenses.application;

import com.expensebot.expenses.domain.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {
    List<Category> findAll();

    Optional<Category> findById(UUID id);
}
