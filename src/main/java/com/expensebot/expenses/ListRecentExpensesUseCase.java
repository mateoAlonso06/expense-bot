package com.expensebot.expenses;

import com.expensebot.accounts.Channel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The expenses a user registered through one channel recently, so a chat can refer to them ("no, eran 15500"). */
public interface ListRecentExpensesUseCase {

    /**
     * @param since only expenses registered at this instant or later (when they were stored, not their date)
     * @return the matching expenses, newest first; empty when there are none
     */
    List<RegisteredExpense> execute(UUID userId, Channel channel, Instant since);
}
