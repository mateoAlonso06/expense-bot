package com.expensebot.expenses.application;

import com.expensebot.expenses.domain.Category;

import java.util.List;

public interface CategoryRepository {
    List<Category> findAll();
}
