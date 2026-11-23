# Correct the last recorded expense from chat (#13)

## Objective
Let a linked user fix a recently stored expense from the chat ("no, eran 15500", "categoría ocio", "la nafta eran 3500") without the web panel.

## Problem / why
Today a correction is read as a new message: at best "no, eran 15500" stores a second expense of 15500. The user has to go to the web panel to fix a typo the bot just confirmed.

## Scope (agreed design)
- The extractor reports a correction as a new `Extraction.Correction(amount, description, categoryName, reference)`: only the new values the user gives (null = unchanged) and how the user names the expense ("la nafta"). Same LLM call; no router yet (revisit in #40).
- The code picks the expense: recent expenses of the conversation (same user and channel, last 30 minutes, configurable) via a new read use case in `expenses` (`ListRecentExpensesUseCase`). No reference -> the last one. Reference -> match by description among the recent ones; one match -> that one; several or none -> ask the user to repeat naming which one (no pending state).
- Editing goes through `EditExpenseUseCase` (#12): only amount, description and category; ownership and validation already there.
- Fixed replies: confirmation; "No encontré un gasto reciente para corregir. Los anteriores los podés editar desde el panel web."; "Solo puedo corregir el monto, la descripción o la categoría."; unknown category lists the available ones.

## Constraints
- Outside-in; small phases with review; one new concept per phase; case table before code.
- The model only reads; every decision and reply is code.
- Effective TDD: off (no project/session setting enables it); ordinary checks per phase. Runner: `./mvnw test` with JDK 21.

## Tasks
- [x] P1 Extractor: `Extraction.Correction`, prompt rules, response fields `correction` and `reference`, mapping; flow answers REPHRASE for a correction until P3; unit tests.
- [ ] P2 Eval: CORRECTION kind in the dataset and scoring, correction cases, recorded run compared with #11's.
- [ ] P3 Flow in `conversation`: pick the expense, edit, reply; tests with mocks (`ListRecentExpensesUseCase` interface only).
- [ ] P4 `expenses`: implement `ListRecentExpensesUseCase` with `JdbcClient`; Postgres tests.
- [ ] P5 Flow test: store -> correct, through the Telegram webhook.

## Acceptance criteria (from #13)
- Given a recent expense in the conversation, a correction updates the editable fields and the bot confirms.
- Given an ambiguous correction, the bot asks which expense is meant.
- Only editable fields can be changed through chat.

## Progress / next step
Branch `feat/correct-last-expense` from `main` (`de1abb3`).
P1 done (not committed, waiting for review): `Extraction.Correction(amount, description, categoryName, reference)` (`describesAPurchase()` false); prompt rules (only new values, null = unchanged, no inferred category, reference, a self-fix inside a new expense is not a correction); response fields `correction` and `reference`; a correction ignores candidates. `RegisterExpenseFromText` answers REPHRASE to a correction until P3 (inside a pending question it repeats the question). Eval report shows corrections; scoring comes in P2. 4 new extractor tests. Full suite: 299 run (298 + 1 opt-in skipped), 0 failures. Next: P2.
