package com.expensebot.conversation.application;

import com.expensebot.expenses.RegisteredExpense;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

/** How an expense and its amounts read in a reply, the same for registering and correcting. */
final class ExpenseText {
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ExpenseText() {
    }

    /** "$15.000,00 en Alimentación (14/11/2026)": always two decimals, so a wrong amount is easy to spot. */
    static String summary(RegisteredExpense expense) {
        // A new NumberFormat per call: the class is not thread-safe, so a shared static instance would be a bug.
        NumberFormat amount = NumberFormat.getNumberInstance(ARGENTINA);
        amount.setMinimumFractionDigits(2);
        amount.setMaximumFractionDigits(2);
        String symbol = Currency.getInstance(expense.currency()).getSymbol(ARGENTINA);
        return "%s%s en %s (%s)".formatted(symbol, amount.format(expense.amount()), expense.categoryName(),
                DATE.format(expense.spentOn()));
    }

    /** "$5.000" or "$3.000,50": no decimals unless the amount has them. */
    static String money(BigDecimal value) {
        NumberFormat amount = NumberFormat.getNumberInstance(ARGENTINA);
        amount.setMinimumFractionDigits(value.stripTrailingZeros().scale() > 0 ? 2 : 0);
        amount.setMaximumFractionDigits(2);
        return "$" + amount.format(value);
    }
}
