package com.expensebot.conversation;

/** Entry point of the conversation module: every channel adapter hands its messages here. */
public interface HandleIncomingMessageUseCase {
    void execute(IncomingMessage message);
}
