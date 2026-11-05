package com.expensebot.expenses.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Category {
    public static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @EqualsAndHashCode.Include
    private final UUID id;
    private final String name;
    private final boolean system;

    private Category(UUID id, String name, boolean system) {
        this.id = id;
        this.name = name;
        this.system = system;
    }

    /** Rebuilds a category that was already validated and stored. There is no create(): the catalog is seeded by Flyway. */
    public static Category restore(UUID id, String name, boolean system) {
        return new Category(id, name, system);
    }
}
