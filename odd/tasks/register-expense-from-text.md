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
- [x] P2 First contact with Spring AI: model configured in `application.yaml`, a test that the `ChatClient.Builder` exists without a key (app and CI start without it), and an opt-in `@Tag("llm")` test with one real OpenAI call (`RUN_LLM_TESTS=true`). The port and the extractor wait for P3, when there is a real contract to put behind it.
- [x] P3 Structured output: port `ExpenseExtractor` + `ExpenseDraft` in `conversation.application`, `SpringAiExpenseExtractor` adapter (`ChatClient`, system prompt, `.entity(...)`), fake `ChatModel` in tests. Unreadable output (not JSON, or no amount) -> empty. The category name -> catalog id mapping with fallback to `Other` moved to P4, next to the orchestrator.
- [x] P4a Category matching: `CategoryMatcher` (package-private, pure) maps the model's category name to a catalog id ignoring case, accents and extra spaces; no fuzzy matching; no match -> empty (the use case then stores `Other`).
- [ ] P4b Integration: `HandleIncomingMessageService` calls the extractor for a linked user, stores the expense, replies with a summary; invalid output -> nothing stored, ask to rephrase; flow test; docs (models table).

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
- Which OpenAI model: decided in P3, `gpt-4o-mini` with temperature 0 (see Progress). `gpt-5-mini` is the fallback if category accuracy turns out to matter more than latency.
- `Money` fixes two decimals for every currency (enough for ARS). If other currencies appear, the scale should come from `Currency.getDefaultFractionDigits()` (JPY has 0, KWD 3) and the column scale would need a migration. Not needed for the MVP.
- Hours of the day vs. date: only the date is stored (`spent_on`).
- Known limitation of `CategoryMatcher`: if two categories offered to the same user normalize to the same text (e.g. "Ocio" and "ocio" once users can create categories in v2), it returns the first one. Not handled in the MVP, where the catalog is the seeded one; when custom categories arrive, either forbid names that collide after normalization or decide on an explicit tie-break.

## Progress / next step
Branch `feat/register-expense` from `main`. P1a done and committed (`Money` is a record, a value-object exception noted in CLAUDE.md): migration V6 (`expense`, check amount > 0, index on `(user_id, spent_on DESC)`), `Expense` domain (amount normalized to 2 decimals, more decimals rejected, blank description -> null), `ExpenseRepository` + `ExpensePersistenceAdapter` on `JdbcClient`. Full suite: 160 tests, 0 failures. Next: P1b.
P1a committed (`7d9892a`). P1b done, awaiting review (not committed): `RegisterExpenseUseCase` + `RegisterExpenseCommand` / `RegisteredExpense` in the expenses public API, `RegisterExpenseService` (category defaults to `Other`, date to today in `expenses.time-zone`, currency from `expenses.currency`), `CategoryRepository.findById`. Full suite: 174 tests, 0 failures. Next: P2 (first Spring AI contact).
P1b committed (`dacf65f`). P2 done, awaiting review (not committed): model in `application.yaml` (`gpt-5-mini`, the documented default made explicit), smoke test for the `ChatClient.Builder`, opt-in `OpenAiConnectionTest` (`@Tag("llm")`, `RUN_LLM_TESTS=true`), `llm` excluded from the fast suite, docs/testing.md. The real call has NOT been run (needs the user's key). Next: P3 (structured output).
P2 committed (`9677809`). P3 done: the extractor reads `{amount, description, category}` with `.entity(ExtractedExpense.class)` (default prompt-based structured output). Only a `JacksonException` (the model did not answer with the JSON) is treated as unreadable; provider failures propagate. Decisions taken with real calls (opt-in `OpenAiExpenseExtractionTest`, 14 messages, some with user-created categories): model `gpt-4o-mini` with `temperature: 0.0` (about 1.3 s per call vs about 5 s for `gpt-5-mini`; same amounts, one category miss that the general prompt fixed); a general system prompt (category by meaning, catch-all `Otros` as last resort) instead of per-case examples, so user-created categories work without touching the prompt; native `useProviderStructuredOutput()` rejected: the generated schema makes amount/category/description required and non-nullable, so "tomé un café" returned amount 0 instead of null. Revisit only if JSON failures show up in practice and the schema can be made nullable. Full suite: 186 tests, 0 failures (2 opt-in skipped). Next: P4.
