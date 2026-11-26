package com.expensebot;

import com.expensebot.accounts.RequestLinkCodeUseCase;
import com.expensebot.conversation.application.ExpenseDraft;
import com.expensebot.conversation.application.Extraction;
import com.expensebot.conversation.application.ExpenseExtractor;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * First flow test: an update posted to the webhook goes through the real controller, mapper, conversation service,
 * reply service, Telegram sender and HTTP client. Only Telegram itself is replaced, by a tiny local HTTP server.
 */
@SpringBootTest(properties = {
        "telegram.bot-token=TEST-TOKEN",
        "telegram.webhook.secret-token=flow-secret"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Tag("flow")
class TelegramReplyFlowTest {
    // Each post needs its own update id: the database keeps the ones already seen, and a repeated
    // id would be dropped as a duplicate.
    private static final AtomicLong UPDATE_IDS = new AtomicLong(System.nanoTime());

    private static final BlockingQueue<CapturedRequest> REQUESTS = new LinkedBlockingQueue<>();
    private static final AtomicInteger TELEGRAM_STATUS = new AtomicInteger(200);
    private static final HttpServer FAKE_TELEGRAM = startFakeTelegram();

    private static final AtomicLong TELEGRAM_USER_IDS = new AtomicLong(1_000);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RequestLinkCodeUseCase requestLinkCode;
    @MockitoBean
    private ExpenseExtractor expenseExtractor;

    @DynamicPropertySource
    static void telegramProperties(DynamicPropertyRegistry registry) {
        registry.add("telegram.api-base-url", () -> "http://localhost:" + FAKE_TELEGRAM.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeTelegram() {
        FAKE_TELEGRAM.stop(0);
    }

    @BeforeEach
    void reset() {
        REQUESTS.clear();
        TELEGRAM_STATUS.set(200);
    }

    @Test
    void anUnlinkedUserIsAskedToLinkTheAccount() throws Exception {
        long telegramUser = TELEGRAM_USER_IDS.incrementAndGet();

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "gasté 500 en el super");

        CapturedRequest request = REQUESTS.poll(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.path()).isEqualTo("/botTEST-TOKEN/sendMessage");
        JsonNode body = new JsonMapper().readTree(request.body());
        assertThat(body.get("chat_id").longValue()).isEqualTo(telegramUser);
        assertThat(body.get("text").asString()).startsWith("Todavía no vinculaste tu cuenta");
    }

    @Test
    void aUserLinksTheAccountAndTheNextMessageIsStoredAsAnExpense() throws Exception {
        long telegramUser = TELEGRAM_USER_IDS.incrementAndGet();
        UUID userId = newUser();
        String code = requestLinkCode.execute(userId).code();
        // The model is replaced: the CI has no API key. The category name comes back without its accent on purpose.
        when(expenseExtractor.extract(eq("gasté 500 en el super"), anyList()))
                .thenReturn(new Extraction.Found(new ExpenseDraft(new BigDecimal("500"), "súper", "alimentacion")));

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, code);
        assertThat(nextReplyText()).startsWith("Cuenta vinculada");

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "gasté 500 en el super");
        assertThat(nextReplyText()).startsWith("Registré $500,00 en Alimentación (");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM expense e JOIN category c ON c.id = e.category_id
                WHERE e.user_id = ? AND e.amount = 500.00 AND c.name = 'Alimentación'""",
                Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void aMessageWithoutAnAmountStoresNothingAndAsksToRephrase() throws Exception {
        long telegramUser = TELEGRAM_USER_IDS.incrementAndGet();
        UUID userId = newUser();
        when(expenseExtractor.extract(eq("tomé un café"), anyList())).thenReturn(new Extraction.MissingAmount(null, null));
        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, requestLinkCode.execute(userId).code());
        assertThat(nextReplyText()).startsWith("Cuenta vinculada");

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "tomé un café");

        assertThat(nextReplyText()).startsWith("No pude entender el gasto");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense WHERE user_id = ?", Integer.class, userId))
                .isZero();
    }

    @Test
    void aMessageWithoutAnAmountIsCompletedByTheAnswerToTheQuestion() throws Exception {
        long telegramUser = TELEGRAM_USER_IDS.incrementAndGet();
        UUID userId = linkedUser(telegramUser);
        when(expenseExtractor.extract(eq("almuerzo con el equipo"), anyList()))
                .thenReturn(new Extraction.MissingAmount("almuerzo", "alimentacion"));
        when(expenseExtractor.extract(eq("8000"), anyList()))
                .thenReturn(new Extraction.Found(new ExpenseDraft(new BigDecimal("8000"), null, null)));

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "almuerzo con el equipo");
        assertThat(nextReplyText()).isEqualTo("¿Cuánto gastaste en almuerzo?");
        assertThat(expenses(userId)).isZero();
        assertThat(pendingQuestions(userId)).isEqualTo(1);

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "8000");
        assertThat(nextReplyText()).startsWith("Registré $8.000,00 en Alimentación (");
        // what was bought and the original message come from the first message, the amount from the answer
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM expense e JOIN category c ON c.id = e.category_id
                WHERE e.user_id = ? AND e.amount = 8000.00 AND e.description = 'almuerzo'
                AND e.original_message = 'almuerzo con el equipo' AND c.name = 'Alimentación'""",
                Integer.class, userId)).isEqualTo(1);
        assertThat(pendingQuestions(userId)).isZero();
    }

    @Test
    void anAmbiguousAmountIsStoredOnceTheUserSaysWhichOneItWas() throws Exception {
        long telegramUser = TELEGRAM_USER_IDS.incrementAndGet();
        UUID userId = linkedUser(telegramUser);
        when(expenseExtractor.extract(eq("super 5000 o 3000"), anyList()))
                .thenReturn(new Extraction.AmbiguousAmount(
                        List.of(new BigDecimal("5000"), new BigDecimal("3000")), "super", "alimentacion"));
        when(expenseExtractor.extract(eq("5000"), anyList()))
                .thenReturn(new Extraction.Found(new ExpenseDraft(new BigDecimal("5000"), null, null)));

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "super 5000 o 3000");
        assertThat(nextReplyText()).isEqualTo("¿Fueron $5.000 o $3.000?");
        assertThat(expenses(userId)).isZero();

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "5000");
        assertThat(nextReplyText()).startsWith("Registré $5.000,00 en Alimentación (");
        assertThat(jdbc.queryForObject("SELECT amount FROM expense WHERE user_id = ?", BigDecimal.class, userId))
                .isEqualByComparingTo("5000");
        assertThat(pendingQuestions(userId)).isZero();
    }

    @Test
    void aCorrectionChangesTheExpenseJustRegistered() throws Exception {
        long telegramUser = TELEGRAM_USER_IDS.incrementAndGet();
        UUID userId = linkedUser(telegramUser);
        when(expenseExtractor.extract(eq("nafta 5000"), anyList()))
                .thenReturn(new Extraction.Found(new ExpenseDraft(new BigDecimal("5000"), "nafta", "transporte")));
        when(expenseExtractor.extract(eq("no, eran 5500"), anyList()))
                .thenReturn(new Extraction.Correction(new BigDecimal("5500"), null, null, null));

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "nafta 5000");
        assertThat(nextReplyText()).startsWith("Registré $5.000,00 en Transporte (");

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "no, eran 5500");
        assertThat(nextReplyText()).startsWith("Corregí nafta: $5.500,00 en Transporte (");
        // the same expense changed: no second one was stored
        assertThat(expenses(userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount FROM expense WHERE user_id = ?", BigDecimal.class, userId))
                .isEqualByComparingTo("5500");
    }

    @Test
    void aCorrectionThatNamesSeveralRecentExpensesAsksWhichOneAndChangesNothing() throws Exception {
        long telegramUser = TELEGRAM_USER_IDS.incrementAndGet();
        UUID userId = linkedUser(telegramUser);
        when(expenseExtractor.extract(eq("nafta YPF 5000"), anyList()))
                .thenReturn(new Extraction.Found(new ExpenseDraft(new BigDecimal("5000"), "nafta YPF", "transporte")));
        when(expenseExtractor.extract(eq("nafta Shell 4000"), anyList()))
                .thenReturn(new Extraction.Found(new ExpenseDraft(new BigDecimal("4000"), "nafta Shell", "transporte")));
        when(expenseExtractor.extract(eq("la nafta eran 3500"), anyList()))
                .thenReturn(new Extraction.Correction(new BigDecimal("3500"), null, null, "la nafta"));
        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "nafta YPF 5000");
        assertThat(nextReplyText()).startsWith("Registré");
        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "nafta Shell 4000");
        assertThat(nextReplyText()).startsWith("Registré");

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "la nafta eran 3500");

        assertThat(nextReplyText()).startsWith("Encontré varios gastos recientes de «la nafta»")
                .contains("nafta YPF ($5.000)", "nafta Shell ($4.000)");
        assertThat(jdbc.queryForList("SELECT amount FROM expense WHERE user_id = ? ORDER BY amount",
                BigDecimal.class, userId))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("4000"), new BigDecimal("5000"));
    }

    @Test
    void anAmountOnlyCorrectionAnswersTheWaitingQuestionInsteadOfCorrecting() throws Exception {
        long telegramUser = TELEGRAM_USER_IDS.incrementAndGet();
        UUID userId = linkedUser(telegramUser);
        when(expenseExtractor.extract(eq("almuerzo con el equipo"), anyList()))
                .thenReturn(new Extraction.MissingAmount("almuerzo", "alimentacion"));
        // "eran 8000" reads like a correction, but the bot is waiting for the amount of the lunch
        when(expenseExtractor.extract(eq("eran 8000"), anyList()))
                .thenReturn(new Extraction.Correction(new BigDecimal("8000"), null, null, null));

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "almuerzo con el equipo");
        assertThat(nextReplyText()).isEqualTo("¿Cuánto gastaste en almuerzo?");

        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, "eran 8000");
        assertThat(nextReplyText()).startsWith("Registré $8.000,00 en Alimentación (");
        assertThat(expenses(userId)).isEqualTo(1);
        assertThat(pendingQuestions(userId)).isZero();
    }

    @Test
    void aCodeCannotBeUsedTwice() throws Exception {
        String code = requestLinkCode.execute(newUser()).code();
        postUpdate(UPDATE_IDS.incrementAndGet(), TELEGRAM_USER_IDS.incrementAndGet(), code);
        assertThat(nextReplyText()).startsWith("Cuenta vinculada");

        postUpdate(UPDATE_IDS.incrementAndGet(), TELEGRAM_USER_IDS.incrementAndGet(), code);

        assertThat(nextReplyText()).startsWith("Ese código no es válido");
    }

    @Test
    void theWebhookStillAnswersOkWhenTelegramRejectsTheReply() throws Exception {
        TELEGRAM_STATUS.set(500);

        postUpdate();

        assertThat(REQUESTS.poll(2, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void theSameUpdateDeliveredTwiceIsAnsweredOnceAndBothDeliveriesGetOk() throws Exception {
        long updateId = UPDATE_IDS.incrementAndGet();

        postUpdate(updateId);
        postUpdate(updateId); // postUpdate expects 200, so the duplicate is acknowledged too

        // The webhook is synchronous: once both calls return, every reply that will ever be sent already was.
        assertThat(REQUESTS).hasSize(1);
    }

    private void postUpdate() throws Exception {
        postUpdate(UPDATE_IDS.incrementAndGet());
    }

    private void postUpdate(long updateId) throws Exception {
        postUpdate(updateId, 42L, "hola");
    }

    private void postUpdate(long updateId, long telegramUser, String text) throws Exception {
        mockMvc.perform(post("/api/telegram/webhook")
                        .header("X-Telegram-Bot-Api-Secret-Token", "flow-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"update_id": %d, "message": {"message_id": 1, "from": {"id": %d}, "text": "%s"}}"""
                                .formatted(updateId, telegramUser, text)))
                .andExpect(status().isOk());
    }

    /** A new user whose Telegram account is linked through the bot, as a real user would do it. */
    private UUID linkedUser(long telegramUser) throws Exception {
        UUID userId = newUser();
        postUpdate(UPDATE_IDS.incrementAndGet(), telegramUser, requestLinkCode.execute(userId).code());
        assertThat(nextReplyText()).startsWith("Cuenta vinculada");
        return userId;
    }

    private int expenses(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM expense WHERE user_id = ?", Integer.class, userId);
    }

    private int pendingQuestions(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM pending_clarification WHERE user_id = ?", Integer.class,
                userId);
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO user_profile (id, email) VALUES (?, ?)", id, id + "@example.com");
        return id;
    }

    private String nextReplyText() throws Exception {
        CapturedRequest request = REQUESTS.poll(2, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        return new JsonMapper().readTree(request.body()).get("text").asString();
    }

    private static HttpServer startFakeTelegram() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                REQUESTS.add(new CapturedRequest(exchange.getRequestURI().getPath(), body));
                int status = TELEGRAM_STATUS.get();
                if (status == 200) {
                    byte[] response = "{\"ok\": true, \"result\": {\"message_id\": 1}}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                } else {
                    exchange.sendResponseHeaders(status, -1);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record CapturedRequest(String path, String body) {
    }
}
