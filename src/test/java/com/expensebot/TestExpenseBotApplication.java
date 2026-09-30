package com.expensebot;

import org.springframework.boot.SpringApplication;

public class TestExpenseBotApplication {

    public static void main(String[] args) {
        SpringApplication.from(ExpenseBotApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
