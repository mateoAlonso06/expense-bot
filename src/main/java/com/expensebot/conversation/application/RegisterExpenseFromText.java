package com.expensebot.conversation.application;

import com.expensebot.conversation.IncomingMessage;
import com.expensebot.expenses.CategoryView;
import com.expensebot.expenses.ListCategoriesUseCase;
import com.expensebot.expenses.RegisterExpenseCommand;
import com.expensebot.expenses.RegisterExpenseUseCase;
import com.expensebot.expenses.RegisteredExpense;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Registers the expense a linked user wrote as free text: the {@link ExpenseExtractor} reads it, the category name is
 * matched against the catalog, and {@link RegisterExpenseUseCase} stores it. Returns the reply for the user; it never
 * throws for a bad message or a provider failure, because the message is already registered as received and the
 * platform will not redeliver it.
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

    private final ListCategoriesUseCase listCategories;
    private final RegisterExpenseUseCase registerExpense;
    private final ExpenseExtractor expenseExtractor;

    RegisterExpenseFromText(ListCategoriesUseCase listCategories,
                            RegisterExpenseUseCase registerExpense,
                            ExpenseExtractor expenseExtractor) {
        this.listCategories = listCategories;
        this.registerExpense = registerExpense;
        this.expenseExtractor = expenseExtractor;
    }

    /** @return the text to send back: a summary of the stored expense, or why nothing was stored */
    String replyTo(UUID userId, IncomingMessage message) {
        String text = message.text();
        if (text == null || text.isBlank()) {
            return ONLY_TEXT; // photos and voice notes come later
        }

        List<CategoryView> categories = listCategories.execute();
        Optional<ExpenseDraft> draft;
        try {
            draft = expenseExtractor.extract(text, categories.stream().map(CategoryView::name).toList());
        } catch (RuntimeException e) {
            // The provider failed (key, network, rate limit).
            log.error("The expense extractor failed channel={} eventId={}", message.channel(), message.eventId(), e);
            return TRY_AGAIN;
        }
        if (draft.isEmpty()) {
            return REPHRASE;
        }

        ExpenseDraft read = draft.get();
        UUID categoryId = CategoryMatcher.match(read.categoryName(), categories).orElse(null);
        try {
            RegisteredExpense stored = registerExpense.execute(new RegisterExpenseCommand(userId, read.amount(),
                    read.description(), categoryId, null, message.channel(), text));
            return summary(stored);
        } catch (IllegalArgumentException e) {
            // The model read something the expense rules reject (for example a zero amount or too many decimals).
            // The use case also throws this for an unknown category, which only happens if the category was deleted
            // between listing and storing.
            log.warn("The extracted expense was rejected channel={} eventId={}: {}",
                    message.channel(), message.eventId(), e.getMessage());
            return REPHRASE;
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
}
