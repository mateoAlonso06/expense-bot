package com.expensebot.conversation.application;

import java.util.List;

/** Reads an expense out of free text. The implementation decides how (today an LLM); callers never see that. */
public interface ExpenseExtractor {

    /**
     * @param categoryNames the catalog categories the result may point to
     * @return what was read: a clear amount, a missing or ambiguous one, or nothing readable
     */
    Extraction extract(String text, List<String> categoryNames);
}
