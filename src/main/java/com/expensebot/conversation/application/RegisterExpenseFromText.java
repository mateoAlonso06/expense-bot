package com.expensebot.conversation.application;

import com.expensebot.conversation.IncomingMessage;
import com.expensebot.conversation.domain.PendingClarification;
import com.expensebot.expenses.CategoryView;
import com.expensebot.expenses.ListCategoriesUseCase;
import com.expensebot.expenses.RegisterExpenseCommand;
import com.expensebot.expenses.RegisterExpenseUseCase;
import com.expensebot.expenses.RegisteredExpense;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers the expense a linked user wrote as free text: the {@link ExpenseExtractor} reads it, the category name is
 * matched against the catalog, and {@link RegisterExpenseUseCase} stores it. When the amount is missing or ambiguous
 * nothing is stored: the bot asks for it and keeps a {@link PendingClarification} until the user answers, writes a new
 * expense, or the question expires.
 * <p>
 * The model only reads the message; this class decides what to do, and every reply is a fixed text. It never throws for
 * a bad message or a provider failure, because the message is already registered as received and the platform will not
 * redeliver it.
 * <p>
 * Deliberately not transactional: the extractor is a slow network call and must not hold a database connection.
 * Storing is one short transaction inside the expenses module.
 */
@Slf4j
@Service
class RegisterExpenseFromText {
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    static final String ONLY_TEXT = "Por ahora solo entiendo mensajes de texto. "
            + "Escribime el gasto, por ejemplo: \"$15.000 carnicería\".";
    static final String REPHRASE = "No pude entender el gasto. "
            + "Escribilo con el monto, por ejemplo: \"$15.000 carnicería\".";
    static final String TRY_AGAIN = "Tuve un problema para procesar tu mensaje. Probá de nuevo en un rato.";
    static final String ASK_AMOUNT = "¿Cuánto gastaste en %s?";
    static final String ASK_WHICH_AMOUNT = "¿Fueron %s?";
    static final String ASK_AGAIN = "No entendí el monto. ";

    private final ListCategoriesUseCase listCategories;
    private final RegisterExpenseUseCase registerExpense;
    private final ExpenseExtractor expenseExtractor;
    private final PendingClarificationRepository pendingClarifications;
    private final Clock clock;
    private final Duration clarificationTtl;

    RegisterExpenseFromText(ListCategoriesUseCase listCategories,
                            RegisterExpenseUseCase registerExpense,
                            ExpenseExtractor expenseExtractor,
                            PendingClarificationRepository pendingClarifications,
                            Clock clock,
                            @Value("${conversation.clarification.ttl:10m}") Duration clarificationTtl) {
        this.listCategories = listCategories;
        this.registerExpense = registerExpense;
        this.expenseExtractor = expenseExtractor;
        this.pendingClarifications = pendingClarifications;
        this.clock = clock;
        this.clarificationTtl = clarificationTtl;
    }

    /**
     * @return the text to send back: a summary of the stored expense, a question, or why nothing was stored
     */
    String replyTo(UUID userId, IncomingMessage message) {
        String text = message.text();
        if (text == null || text.isBlank()) {
            return ONLY_TEXT; // photos and voice notes come later
        }

        Instant now = clock.instant();
        // An expired question is ignored: the message is read as if the bot had never asked.
        Optional<PendingClarification> pending = pendingClarifications.find(userId, message.channel())
                .filter(p -> !p.isExpiredAt(now));

        List<CategoryView> categories = listCategories.execute();
        Extraction extraction;
        try {
            extraction = expenseExtractor.extract(text, categories.stream().map(CategoryView::name).toList());
        } catch (RuntimeException e) {
            // The provider failed (key, network, rate limit). A pending question stays as it was.
            log.error("The expense extractor failed channel={} eventId={}", message.channel(), message.eventId(), e);
            return TRY_AGAIN;
        }

        if (pending.isPresent()) {
            return answerTo(pending.get(), extraction, userId, message, categories, now);
        }
        return newMessage(extraction, userId, message, categories, now);
    }

    /**
     * No question is waiting: store the expense, or ask for its amount.
     */
    private String newMessage(Extraction extraction, UUID userId, IncomingMessage message,
                              List<CategoryView> categories, Instant now) {
        return switch (extraction) {
            case Extraction.Found found -> storeNew(found, userId, message, categories);
            case Extraction.MissingAmount missing when missing.describesAPurchase() ->
                    askForAmount(missing, userId, message, now);
            case Extraction.AmbiguousAmount ambiguous -> askWhichAmount(ambiguous, userId, message, now);
            // nothing to ask about: neither an amount nor what was bought
            case Extraction.MissingAmount missing -> REPHRASE;
            case Extraction.Unreadable unreadable -> REPHRASE;
        };
    }

    private String storeNew(Extraction.Found found, UUID userId, IncomingMessage message,
                            List<CategoryView> categories) {
        return store(userId, message, found.draft(), message.text(), categories).orElse(REPHRASE);
    }

    private String askForAmount(Extraction.MissingAmount missing, UUID userId, IncomingMessage message, Instant now) {
        return ask(PendingClarification.missingAmount(userId, message.channel(), message.text(),
                missing.description(), missing.categoryName(), now.plus(clarificationTtl)));
    }

    private String askWhichAmount(Extraction.AmbiguousAmount ambiguous, UUID userId, IncomingMessage message,
                                  Instant now) {
        return ask(PendingClarification.ambiguousAmount(userId, message.channel(), message.text(),
                ambiguous.description(), ambiguous.categoryName(), ambiguous.candidates(),
                now.plus(clarificationTtl)));
    }

    /**
     * A question is waiting. A message that only gives an amount answers it; a message that describes something
     * bought is a new expense and replaces the question; anything else repeats the question.
     */
    private String answerTo(PendingClarification question, Extraction extraction, UUID userId,
                            IncomingMessage message, List<CategoryView> categories, Instant now) {
        if (extraction.describesAPurchase()) {
            if (extraction instanceof Extraction.Found) {
                // the new expense is stored and the old question is dropped; asking replaces it on its own
                pendingClarifications.delete(userId, message.channel());
            }
            return newMessage(extraction, userId, message, categories, now);
        }

        if (extraction instanceof Extraction.Found(ExpenseDraft answer)) {
            // The amount comes from the answer; what was bought, the category and the original message from the
            // question. The question is removed only once the expense is stored.
            var completed = new ExpenseDraft(answer.amount(), question.getDescription(), question.getCategoryName());
            Optional<String> summary = store(userId, message, completed, question.getOriginalMessage(), categories);
            if (summary.isPresent()) {
                pendingClarifications.delete(userId, message.channel());
                return summary.get();
            }
        }
        return ASK_AGAIN + question(question);
    }

    private String ask(PendingClarification clarification) {
        pendingClarifications.save(clarification);
        return question(clarification);
    }

    private static String question(PendingClarification clarification) {
        if (!clarification.isAmbiguous()) {
            return ASK_AMOUNT.formatted(clarification.getDescription());
        }
        List<String> amounts = clarification.getAmountCandidates().stream().map(RegisterExpenseFromText::money)
                .toList();
        String options = String.join(", ", amounts.subList(0, amounts.size() - 1)) + " o " + amounts.getLast();
        return ASK_WHICH_AMOUNT.formatted(options);
    }

    /**
     * @return the summary for the user, or empty when the expense rules reject what was read
     */
    private Optional<String> store(UUID userId, IncomingMessage message, ExpenseDraft draft, String originalMessage,
                                   List<CategoryView> categories) {
        UUID categoryId = CategoryMatcher.match(draft.categoryName(), categories).orElse(null);
        try {
            RegisteredExpense stored = registerExpense.execute(new RegisterExpenseCommand(userId, draft.amount(),
                    draft.description(), categoryId, null, message.channel(), originalMessage));
            return Optional.of(summary(stored));
        } catch (IllegalArgumentException e) {
            // The model read something the expense rules reject (for example a zero amount or too many decimals).
            // The use case also throws this for an unknown category, which only happens if the category was deleted
            // between listing and storing.
            log.warn("The extracted expense was rejected channel={} eventId={}: {}",
                    message.channel(), message.eventId(), e.getMessage());
            return Optional.empty();
        }
    }

    private static String summary(RegisteredExpense expense) {
        // A new NumberFormat per call: the class is not thread-safe, so a shared static instance would be a bug.
        NumberFormat amount = NumberFormat.getNumberInstance(ARGENTINA);
        amount.setMinimumFractionDigits(2);
        amount.setMaximumFractionDigits(2);
        String symbol = Currency.getInstance(expense.currency()).getSymbol(ARGENTINA);
        return "Registré %s%s en %s (%s).".formatted(symbol, amount.format(expense.amount()),
                expense.categoryName(), DATE.format(expense.spentOn()));
    }

    /**
     * "$5.000" or "$3.000,50": no decimals unless the amount has them.
     */
    private static String money(BigDecimal value) {
        NumberFormat amount = NumberFormat.getNumberInstance(ARGENTINA);
        amount.setMinimumFractionDigits(value.stripTrailingZeros().scale() > 0 ? 2 : 0);
        amount.setMaximumFractionDigits(2);
        return "$" + amount.format(value);
    }
}
