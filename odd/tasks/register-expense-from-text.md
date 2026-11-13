# Feature: register-expense-from-text (issue #10)

## Objective
A linked user writes free text to the bot ("$15.000 carnicería") and an expense is stored with amount, description, date (default today) and category; the bot replies with a summary. The LLM only extracts structured data; storing is plain application code.

## Constraints
- Phases stay small and are reviewed by the user one at a time. The LLM enters last, so everything before it is tested without a model.
- The user is new to Spring AI: every Spring AI concept is explained the first time it appears (what, why, the alternative not chosen).
- Only the amount is mandatory; a low-confidence category never triggers a question. Category falls back to `Other` (`Category.OTHER_ID`).
- Money: `NUMERIC(19,2)` / `BigDecimal`, explicit currency (ARS). Date, source channel and original message are immutable.
- Module rules: `expenses` exposes use cases in its base package; `conversation` reaches it only through those. No cross-module entities; references by id.
- Domain objects: private constructor + `create`/`restore`, no records, no Spring in `domain`. Audit columns belong to persistence.
- Secrets: `OPENAI_API_KEY` comes from the environment (`.env`), never committed; the CI must not need a real key (tests use a fake `ChatModel`).
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).

## Tasks
- [x] P1a Storage: migration `expense`, domain `Expense` (validated `create`), `ExpenseRepository` + persistence adapter + tests.
- [x] P1b Use case: `RegisterExpenseUseCase` (command in, summary out): category defaults to `Other`, date defaults to today in a configured time zone, currency ARS; `CategoryRepository.findById`; tests.
- [ ] P2 First `ChatClient`: Spring AI + OpenAI configuration (app and CI start without a key), one minimal call behind a port, fake `ChatModel` in tests.
- [ ] P3 Structured output: the model returns a Java object (amount, description, category name); schema validation; category name -> catalog id with fallback to `Other`.
- [ ] P4 Integration: `HandleIncomingMessageService` calls the agent for a linked user, stores the expense, replies with a summary; invalid output -> nothing stored, ask to rephrase; flow test; docs (models table).

## Acceptance criteria (from #10)
- Linked user + a message with an amount: expense stored with amount, description, date (default today) and category.
- A clear category cue uses the LLM-inferred catalog category; no cue stores `Other`; a category outside the catalog falls back to `Other`.
- The amount is the only mandatory extracted field.
- The bot replies with a summary (amount, category, date).
- LLM output that fails schema validation: nothing stored, the user is asked to rephrase.
- An unlinked Telegram user gets linking instructions (done in #5).

## Open points
- Time zone for "today": default `America/Argentina/Buenos_Aires`, as a property.
- Whether an expense without description is stored with a null description (decided in P1a: nullable).
- Which OpenAI model (decide in P2 after checking current model names and prices).
- `Money` fixes two decimals for every currency (enough for ARS). If other currencies appear, the scale should come from `Currency.getDefaultFractionDigits()` (JPY has 0, KWD 3) and the column scale would need a migration. Not needed for the MVP.
- Hours of the day vs. date: only the date is stored (`spent_on`).

## Progress / next step
Branch `feat/register-expense` from `main`. P1a done and committed (`Money` is a record, a value-object exception noted in CLAUDE.md): migration V6 (`expense`, check amount > 0, index on `(user_id, spent_on DESC)`), `Expense` domain (amount normalized to 2 decimals, more decimals rejected, blank description -> null), `ExpenseRepository` + `ExpensePersistenceAdapter` on `JdbcClient`. Full suite: 160 tests, 0 failures. Next: P1b.
P1a committed (`7d9892a`). P1b done, awaiting review (not committed): `RegisterExpenseUseCase` + `RegisterExpenseCommand` / `RegisteredExpense` in the expenses public API, `RegisterExpenseService` (category defaults to `Other`, date to today in `expenses.time-zone`, currency from `expenses.currency`), `CategoryRepository.findById`. Full suite: 174 tests, 0 failures. Next: P2 (first Spring AI contact).
