# Feature: clarify-missing-amount (issue #11)

## Objective
When a message describes an expense without an amount, or with conflicting amounts, the bot asks for the amount and stores nothing until the user answers. The pending question belongs to the conversation and expires.

## Problem / why
Today a message without amount gets "No pude entender el gasto…" and is lost; a message with two amounts is stored with whichever one the model picked, silently. The amount is the only field that cannot be guessed.

## Design (agreed with the user)
- The extractor reports what it found, it decides nothing: amount found, amount missing (with description and category if any), ambiguous amount (the candidates), or unreadable answer. Self-corrections ("5000, perdón, 6000") are resolved by the model and are not ambiguous.
- `RegisterExpenseFromText` (application) decides: missing amount with a description -> ask; ambiguous -> ask with the candidates; missing amount and no description -> not an expense, current "rephrase" reply.
- Pending question in a table `pending_clarification`, one row per conversation (channel + external user id): original message, description, category name, candidates, expiry. Expiry checked when read; no cleanup job.
- Next message while pending: only an amount -> completes the pending expense and clears it; a full new expense -> stored, pending discarded; anything else without amount -> ask again. Expired pending -> the message is handled as new.
- TTL property `conversation.clarification.ttl`, default 10m.
- A low-confidence category never asks (already true: unknown -> `Otros`).
- Prompt changes -> eval dataset gains missing/ambiguous cases and a recorded run.
- Discarded: Spring AI chat memory (less predictable, harder to test, more tokens; kept for #40), in-memory pending (lost on restart, single instance), cleanup job, multi-expense messages, a "cancel" command.
- Known limit: "mañana tengo que pagar el alquiler" has a description and no amount, so the bot asks.

## Constraints
- Hexagonal rules (`ArchitectureTest`); persistence with `JdbcClient`, tested on PostgreSQL.
- The extractor stays a port; the domain of `expenses` is unchanged.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21); eval: `RUN_LLM_TESTS=true ./mvnw test -Dtest=ExtractionEval`.

## Tasks
- [x] P1 Extractor result: sealed `Extraction` (found / missing amount / ambiguous / unreadable) in the port, prompt and response schema with amount candidates, `RegisterExpenseFromText` maps every non-found result to today's reply for now; unit tests.
- [x] P2 Eval: dataset expects an outcome kind (found, missing with or without description, ambiguous with candidates), new missing/ambiguous cases, scoring and report updated, recorded run.
- [ ] P3 Persistence: migration `pending_clarification`, port + `JdbcClient` adapter (upsert, find, delete), Postgres tests.
- [ ] P4 Flow: ask, complete, discard, expire in `RegisterExpenseFromText`; TTL property; tests with mocks.
- [ ] P5 Flow test: message without amount -> question -> amount -> stored expense, through the Telegram webhook.

## Acceptance criteria (from #11)
- Given no amount, or two conflicting amounts, then the bot asks for it and stores nothing until answered.
- A low-confidence category never triggers a question; it falls back to `Other`.
- The pending clarification is tied to the conversation and expires after a timeout.

## Progress / next step
Branch `feat/clarify-missing-amount` from `main` (`e187c74`).
P1 done (not committed, waiting for review): sealed `Extraction` (`Found`, `MissingAmount`, `AmbiguousAmount` with at least two distinct candidates, `Unreadable`) replaces `Optional<ExpenseDraft>` in `ExpenseExtractor`. Prompt rule and response field `amountCandidates`; the adapter drops repeated candidates by value, keeps message order, and lets two or more candidates win over a single amount. `RegisterExpenseFromText` stores only `Found`; every other result keeps the "rephrase" reply until P4, so an ambiguous message is no longer stored with a guessed amount. `ExtractionEval` maps non-`Found` to "no expense" until P2. Full suite: 269 run (268 + 1 opt-in skipped), 0 failures; docs/testing.md counts refreshed. Committed as `43232ea`.
P2 done (not committed, waiting for review): `ExtractionCase` gains `amountCandidates` and a derived `Kind` (FOUND, AMBIGUOUS, ASK_AMOUNT = no amount but a description, NOT_AN_EXPENSE = neither); the loader validates them (amount and candidates exclusive, two or more distinct positive candidates, category rules per kind). "Amount" now scores the amount outcome per kind; category is scored on every case that expects one (missing-amount cases included). Report shows expected and read kind. Dataset: 46 cases (cafe and future now ask for the amount; question and "hola" are not expenses; 3 ambiguous, 3 more missing, 2 self-corrections). Recorded run `2026-11-19_1230_gpt-4o-mini_14e411ba` (dataset `1b966855`): amount 46/46, category 38/44 (86.4%), Other fallback 2/41. A second, unrecorded run: amount 45/46 ("uber 3000 no, 3500" read as ambiguous, so the bot would ask instead of storing), category 37/44. On the 34 category cases shared with the baseline, the category got slightly worse (baseline 30; now 29 and 28): "veterinario" (custom Mascotas -> Salud) and "estacionamiento" (-> Servicios) failed in both runs, "rappi" was fixed in one. Full suite: 272 run (271 + 1 opt-in skipped), 0 failures. Next: P3.
