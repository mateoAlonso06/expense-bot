package com.billsbot;

import org.springframework.boot.SpringApplication;

public class TestBillsBotApplication {

    public static void main(String[] args) {
        SpringApplication.from(BillsBotApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
