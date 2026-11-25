# Correct the last recorded expense from chat (#13)

## Objective
Let a linked user fix a recently stored expense from the chat ("no, eran 15500", "categoría ocio", "la nafta eran 3500") without the web panel.

## Problem / why
Today a correction is read as a new message: at best "no, eran 15500" stores a second expense of 15500. The user has to go to the web panel to fix a typo the bot just confirmed.

## Scope (agreed design)
- The extractor reports a correction as a new `Extraction.Correction(amount, description, categoryName, reference)`: only the new values the user gives (null = unchanged) and how the user names the expense ("la nafta"). Inside the adapter, a first LLM call classifies the intent (expense or correction), then a focused prompt reads it (the expense prompt from #11, or a correction prompt). The `ExpenseExtractor` port does not change. The classifier gains a third intent (query) in #40; Jev for the classifier is the spike #44.
- The code picks the expense: recent expenses of the conversation (same user and channel, last 30 minutes, configurable) via a new read use case in `expenses` (`ListRecentExpensesUseCase`). No reference -> the last one. Reference -> match by description among the recent ones; one match -> that one; several or none -> ask the user to repeat naming which one (no pending state).
- Editing goes through `EditExpenseUseCase` (#12): only amount, description and category; ownership and validation already there.
- Fixed replies: confirmation; "No encontré un gasto reciente para corregir. Los anteriores los podés editar desde el panel web."; "Solo puedo corregir el monto, la descripción o la categoría."; unknown category lists the available ones.

## Constraints
- Outside-in; small phases with review; one new concept per phase; case table before code.
- The model only reads; every decision and reply is code.
- Effective TDD: off (no project/session setting enables it); ordinary checks per phase. Runner: `./mvnw test` with JDK 21.

## Tasks
- [x] P1 Extractor: `Extraction.Correction`, prompt rules, response fields `correction` and `reference`, mapping; flow answers REPHRASE for a correction until the flow phase; unit tests. (`e29332e`)
- [x] P2 Eval: CORRECTION kind in the dataset and scoring, 8 correction cases, recorded run (the "before" run that motivated the classifier).
- [x] P3 Classifier inside the adapter: intent call (expense vs correction, few-shot incl. self-corrections as expense), expense prompt back to the #11 version, focused correction prompt; `ExpenseExtractor` port unchanged; eval extractor hash covers the whole `llm` package; recorded run compared with P2 and #11. (`9e50eb6`)
- [x] P4 Flow: `CorrectRecentExpense` in `conversation.application`, called from `RegisterExpenseFromText` for a `Correction`; pending-question rule (an amount-only correction without reference answers the waiting question); tests with mocks (`ListRecentExpensesUseCase` interface only). Must include an explicit test: bot asks "¿Cuánto gastaste en café?", user answers "eran 15000" (likely classified CORRECTION) -> the expense is stored, not the question repeated. Until P4 this is a regression from #11 (the classifier has no pending-question context and the eval cannot see it), so P3 must not be merged alone.
- [x] P5 `expenses`: `ListRecentExpensesUseCase` (user, channel, since; newest first) with `JdbcClient`; Postgres tests. Done before P4: a bean with no implementation would stop the application context. The window property (30 minutes) moves to P4 (it belongs to the conversation). (`a31ae48`)
- [ ] P6 Flow test: store -> correct, through the Telegram webhook.

## Acceptance criteria (from #13)
- Given a recent expense in the conversation, a correction updates the editable fields and the bot confirms.
- Given an ambiguous correction, the bot asks which expense is meant.
- Only editable fields can be changed through chat.

## Progress / next step
Branch `feat/correct-last-expense` from `main` (`de1abb3`).
P1 done: `Extraction.Correction(amount, description, categoryName, reference)` (`describesAPurchase()` false); prompt rules (only new values, null = unchanged, no inferred category, reference, a self-fix inside a new expense is not a correction); response fields `correction` and `reference`; a correction ignores candidates. `RegisterExpenseFromText` answers REPHRASE to a correction until P3 (inside a pending question it repeats the question). Eval report shows corrections; scoring comes in P2. 4 new extractor tests. Full suite: 299 run (298 + 1 opt-in skipped), 0 failures. Committed as `e29332e`.
P2 committed: eval knows CORRECTION (`correction`, `reference` in the dataset; a correction is right when it changes exactly the expected fields: same amount or none, description only if expected, no category unless expected). 8 correction cases (54 total). Recorded run `2026-11-23_1535_gpt-4o-mini_0f27b976`: amount 46/54 (85.2%), category 39/47 (83.0%), Other fallback 3/44. Finding: the correction rule works for plain cases ("no, eran 15500", "me equivoqué, fueron 2300", "perdón, eran 1800 y era de salud") but misses 5/8 (with a reference it reads a new expense; "categoría ocio" and "cambiá la fecha" read as not an expense; "era asado, no carnicería" loses the new description) and breaks both self-corrections ("uber 3000 no, 3500" would edit the previous expense instead of storing a new one). Category also worse (carnicería -> Otros, gas -> Transporte, osde -> Servicios).
Decision after the P2 run: intent detection moves to a separate call inside the adapter (classifier, then the expense prompt from #11 or a correction prompt); the `ExpenseExtractor` port is unchanged. Plan: P2 committed as the "before" run, P3 classifier, P4 `CorrectRecentExpense`, P5 `ListRecentExpensesUseCase`, P6 flow test. Jev (TypeSafe AI) for the classifier is a later spike: #44.
P2 committed (last commit of the branch). Full suite: 304 run (303 + 1 opt-in skipped), 0 failures.
P3 code done (not committed): `IntentClassifier` (own class and ChatClient in `infrastructure/llm`, returns `Optional<Intent>`; empty = unreadable) injected into `SpringAiExpenseExtractor`, which routes to `EXPENSE_PROMPT` (back to the #11 version) or `CORRECTION_PROMPT` with `.system(...)` per call; `ExtractedExpense` back to #11, new `ExtractedCorrection`. Classifier is a concrete class for now; it becomes an interface in #44 only if Jev gets a second implementation (agreed with the user). Eval hash covers every file of the `llm` package. Tests: `IntentClassifierTest` (6), extractor tests mock the classifier. Full suite: 312 run (311 + 1 opt-in skipped), 0 failures.
Run `66379635` recorded: amount 52/54 (96.3%), category 41/47 (87.2%), Other fallback 2/44 (4.5%), corrections 7/8, latency avg 2121 ms (max 7485). Failures: "uber 3000 no, 3500" read as ambiguous (classifier right; passed in #11 with the same prompt, likely run variance, safe failure); "el uber ponelo en transporte" put "uber" in description as well as reference (silent rename risk in P4).
Correction prompt adjusted: the words that name the expense go only in reference, description only when the user explicitly gives a new one. Stale docs fixed (eval hash label, two calls per case in README and docs/testing.md, `ExpenseExtractor` javadoc mentions corrections).
Run `bd8cc4e4` with the adjusted prompt: amount 54/54 (100%), category 40/47 (85.1%), Other fallback 2/44 (4.5%), corrections 8/8, self-corrections 2/2, latency avg 1900 ms (max 4531). Fixed vs `66379635`: "el uber ponelo en transporte" (description null now) and "uber 3000 no, 3500". One category change: "pagué 18.000 el veterinario" -> Salud instead of the custom category (expense prompt untouched: run variance).
P3 committed as `9e50eb6`.
P4 case table agreed (see Engram decision "Decisions before P4"): no reference -> last; reference -> match by description (leading article stripped, case/accents normalized, contains): one -> edit, several -> ask, none -> list recents; no recents -> panel message; nothing editable -> fixed reply; unknown category -> list categories (no Other fallback); invalid amount -> fixed reply. Pending question: amount-only correction answers it (regression fix); any other correction repeats the question (option A). Recent = per user and channel, 30 min, from created_at.
Order changed with the user: P5 before P4.
P5 done (not committed): `ListRecentExpensesUseCase` + `ListRecentExpensesService`, `ExpenseRepository.findRecent` returns `RegisteredExpense` (read model, JOIN for the category name), ordered by created_at DESC, id DESC. Verified: the PostgreSQL driver rejects `Instant` ("Can't infer the SQL type"), so `since` is bound as `OffsetDateTime`. Tests: 4 Postgres + 2 mocks. Full suite: 317 run (316 + 1 opt-in skipped), 0 failures.
P5 committed as `a31ae48`.
P4 done (not committed): `CorrectRecentExpense` (package-private service in `conversation.application`; window `conversation.correction.window`, 30m) applies the case table through `EditExpenseUseCase`; `RegisterExpenseFromText` routes a `Correction` to it and, while a question waits, turns an amount-only correction into the answer (regression fix) and repeats the question for any other correction. `Extraction.Correction` gained `changesNothing()` and `onlyChangesTheAmount()`. Reply formatting moved to `ExpenseText` (shared by registering and correcting); `CategoryMatcher.normalize` is package-private and reused for the reference. Bug found by a test: a reference that is only an article ("la") kept "la" as the name; the article pattern now also matches at the end. Tests: 12 `CorrectRecentExpenseTest`, 3 new in `RegisterExpenseFromTextTest`, 2 new in `ExtractionTest`. Full suite: 334 run (333 + 1 opt-in skipped), 0 failures.
Readability pass agreed with the user: `replyTo` reads as steps; `candidates(...)`, `describes(...)`, `searchableName(...)` and `edit(...)` named; the three catches stay (each documented failure of the edit has its own reply).
P4 committed.
Next: P6 (flow test through the Telegram webhook: store -> correct), then the PR closing #13.
Planned later: #44 Jev for the intent call; #45 compare newer LLMs (configurable model, mixed cheap-classifier/strong-reader setup).
