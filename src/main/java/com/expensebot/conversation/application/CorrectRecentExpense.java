package com.expensebot.conversation.application;

import com.expensebot.accounts.Channel;
import com.expensebot.expenses.CategoryView;
import com.expensebot.expenses.EditExpenseCommand;
import com.expensebot.expenses.EditExpenseUseCase;
import com.expensebot.expenses.ExpenseNotFoundException;
import com.expensebot.expenses.ListRecentExpensesUseCase;
import com.expensebot.expenses.RegisteredExpense;
import com.expensebot.expenses.UnknownCategoryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Applies a correction the user wrote in the chat ("no, eran 15500", "la nafta eran 3500", "categoría ocio") to one of
 * the expenses the user registered recently through the same channel. Only the amount, the description and the
 * category change, through {@link EditExpenseUseCase}.
 * <p>
 * Which expense: without a reference, the most recent one; with a reference, the only recent one whose description
 * contains it. Several matches or none are never guessed: the bot says so and changes nothing. Every reply is a fixed
 * text.
 */
@Slf4j
@Service
class CorrectRecentExpense {
    static final String CORRECTED = "Corregí %s: %s.";
    static final String NO_RECENT = "No encontré un gasto reciente para corregir. "
            + "Los anteriores los podés editar desde el panel web.";
    static final String NOT_EDITABLE = "Solo puedo corregir el monto, la descripción o la categoría.";
    static final String UNKNOWN_CATEGORY = "No tengo la categoría «%s». Las disponibles son: %s.";
    static final String SEVERAL_MATCH = "Encontré varios gastos recientes de «%s»: %s. "
            + "Repetí la corrección nombrando cuál.";
    static final String NONE_MATCH = "No encontré un gasto reciente de «%s». Los recientes son: %s.";
    static final String INVALID = "No pude corregir el gasto: el valor nuevo no es válido.";

    // "la nafta" names the same expense as "nafta YPF": a leading article is not part of the name.
    private static final Pattern LEADING_ARTICLE = Pattern.compile("^(el|la|los|las|lo|un|una|unos|unas)(\\s+|$)");

    private final ListRecentExpensesUseCase listRecentExpenses;
    private final EditExpenseUseCase editExpense;
    private final Clock clock;
    private final Duration window;

    CorrectRecentExpense(ListRecentExpensesUseCase listRecentExpenses,
                         EditExpenseUseCase editExpense,
                         Clock clock,
                         @Value("${conversation.correction.window:30m}") Duration window) {
        this.listRecentExpenses = listRecentExpenses;
        this.editExpense = editExpense;
        this.clock = clock;
        this.window = window;
    }

    /**
     * @param categories the catalog, to turn the category name of the correction into an id
     * @return the text to send back: what was corrected, or why nothing was
     */
    String replyTo(UUID userId, Channel channel, Extraction.Correction correction, List<CategoryView> categories) {
        if (correction.changesNothing()) {
            return NOT_EDITABLE;
        }
        // Unlike a new expense, an unknown category does not fall back to Other: the user asked for a specific one.
        Optional<UUID> categoryId = CategoryMatcher.match(correction.categoryName(), categories);
        if (correction.categoryName() != null && categoryId.isEmpty()) {
            return unknownCategory(correction.categoryName(), categories);
        }

        List<RegisteredExpense> recent = listRecentExpenses.execute(userId, channel, clock.instant().minus(window));
        if (recent.isEmpty()) {
            return NO_RECENT;
        }
        List<RegisteredExpense> candidates = candidates(recent, correction.reference());
        if (candidates.size() > 1) {
            return SEVERAL_MATCH.formatted(correction.reference(), list(candidates));
        }
        if (candidates.isEmpty()) {
            return NONE_MATCH.formatted(correction.reference(), list(recent));
        }
        return edit(userId, candidates.getFirst(), correction, categoryId.orElse(null), categories);
    }

    /** Without a reference, the most recent expense; with one, every recent expense whose description contains it. */
    private static List<RegisteredExpense> candidates(List<RegisteredExpense> recent, String reference) {
        String name = searchableName(reference);
        if (name == null) {
            return List.of(recent.getFirst());
        }
        return recent.stream().filter(expense -> describes(expense, name)).toList();
    }

    private static boolean describes(RegisteredExpense expense, String name) {
        return expense.description() != null && CategoryMatcher.normalize(expense.description()).contains(name);
    }

    /** "la Nafta" becomes "nafta"; null when nothing is left to search for (no reference, or only "la"). */
    private static String searchableName(String reference) {
        if (reference == null) {
            return null;
        }
        String name = LEADING_ARTICLE.matcher(CategoryMatcher.normalize(reference)).replaceFirst("");
        return name.isEmpty() ? null : name;
    }

    /** Each documented failure of the edit gets its own reply; anything else is the caller's "try again". */
    private String edit(UUID userId, RegisteredExpense target, Extraction.Correction correction, UUID categoryId,
                        List<CategoryView> categories) {
        try {
            RegisteredExpense corrected = editExpense.execute(new EditExpenseCommand(userId, target.id(),
                    correction.amount(), correction.description(), categoryId));
            String name = corrected.description() == null ? "el gasto" : corrected.description();
            return CORRECTED.formatted(name, ExpenseText.summary(corrected));
        } catch (ExpenseNotFoundException e) {
            // Deleted from the web panel between listing and editing.
            return NO_RECENT;
        } catch (UnknownCategoryException e) {
            // The category was deleted between listing the catalog and editing.
            return unknownCategory(correction.categoryName(), categories);
        } catch (IllegalArgumentException e) {
            // The expense rules reject the new value (for example a zero amount or too many decimals).
            log.warn("The correction was rejected expenseId={}: {}", target.id(), e.getMessage());
            return INVALID;
        }
    }

    private static String unknownCategory(String name, List<CategoryView> categories) {
        return UNKNOWN_CATEGORY.formatted(name,
                categories.stream().map(CategoryView::name).collect(Collectors.joining(", ")));
    }

    /** "nafta YPF ($5.000), uber ($3.000)": the description, or the category when there is none. */
    private static String list(List<RegisteredExpense> expenses) {
        return expenses.stream()
                .map(expense -> "%s (%s)".formatted(
                        expense.description() == null ? expense.categoryName() : expense.description(),
                        ExpenseText.money(expense.amount())))
                .collect(Collectors.joining(", "));
    }
}
