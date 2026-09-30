package com.expensebot.expenses.domain;

import lombok.Getter;

import java.util.UUID;

@Getter
public class Category {
    public static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final UUID id;
    private final String name;
    private final boolean system;

    public Category(UUID id, String name, boolean system) {
        this.id = id;
        this.name = name;
        this.system = system;
    }
}
