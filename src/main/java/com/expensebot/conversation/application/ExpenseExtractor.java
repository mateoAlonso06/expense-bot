package com.expensebot.conversation.application;

import java.util.List;
import java.util.Optional;

/** Reads an expense out of free text. The implementation decides how (today an LLM); callers never see that. */
public interface ExpenseExtractor {

    /**
     * @param categoryNames the catalog categories the result may point to
     * @return the expense the text describes, or empty if no amount could be read from it
     */
    Optional<ExpenseDraft> extract(String text, List<String> categoryNames);
}
