package com.expensebot;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Tag("smoke")
class ExpenseBotApplicationTests {

    @Autowired
    private ChatClient.Builder chatClientBuilder;

    @Test
    void contextLoads() {
    }

    // Spring AI configures the builder from the starter on the classpath; no API key is needed to create it,
    // only to make a call. That is what lets the application and the CI start without a key.
    @Test
    void exposesAChatClientBuilderWithoutAnApiKey() {
        assertThat(chatClientBuilder).isNotNull();
    }

}
