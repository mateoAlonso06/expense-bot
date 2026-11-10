package com.expensebot.accounts.application;

import com.expensebot.accounts.domain.LinkCode;

public interface LinkCodeRepository {
    void save(LinkCode linkCode);
}
