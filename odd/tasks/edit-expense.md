# Feature: edit-expense (issue #12)

## Objective
A web user edits the amount, description or category of one of their own expenses through the API. Date, source channel and original message never change.

## Problem / why
An expense the LLM recorded wrong (amount misread, wrong category) can only be fixed today by touching the database. #13 (correct the last expense from chat) builds on this.

## Scope
- `PATCH /api/expenses/{id}` for the authenticated user (JWT `sub`).
- Partial update: a field left out (or null) stays as it is. A blank description clears it (the domain already turns blank into null).
- Out of scope: listing or reading expenses (no GET yet), deleting, custom categories (v2).

## Constraints
- Hexagonal module rules (`ArchitectureTest`): the controller only uses the `expenses` public API; the domain has no Spring.
- Domain objects: private constructor, `create` / `restore`; invariants live in the domain (amount positive, description at most 255 characters).
- Another user's expense and an unknown id are indistinguishable: both 404, so ids of other users are not revealed.
- Errors as `ProblemDetail` (RFC 9457). There is no error handling in the app yet.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).

## Tasks
- [x] P1 Domain + persistence: `Expense.restore`, `changeAmount` / `changeDescription` / `changeCategory` (the only mutators, so the rest stays immutable); `ExpenseRepository.findByIdAndUserId` and `update` (only category, amount, description, `updated_at`). Tests.
- [x] P2 Use case: `EditExpenseUseCase` + `EditExpenseCommand` in the public API, `EditExpenseService` (load owned expense or not-found exception, check the category exists, apply changes, save), result record. Tests with mocks.
- [ ] P3 Web: `ExpenseController` `PATCH /api/expenses/{id}`, request DTO with Bean Validation that rejects unknown and immutable fields, `@RestControllerAdvice` with `ProblemDetail` (404, 400). `@WebMvcTest` tests; docs/testing.md counts.

## Acceptance criteria (from #12)
- An owned expense can have its amount, description and category updated.
- Date, source channel and original message are immutable; attempts to change them are rejected.
- Another user's expense or an unknown id answers 404.
- Amount must be positive; category must exist.

## Progress / next step
Branch `feat/edit-expense` from `main` (`aa2fe4e`). P1 done and committed: `Expense` editable fields are no longer `final` and change only through `changeAmount` (positive, same currency) / `changeDescription` / `changeCategory`; `restore` added. `findByIdAndUserId` filters by owner in SQL; `update` writes only category, amount, description and `updated_at`, and fails if no row matched. Full suite: 221 tests, 0 failures (2 opt-in skipped). Next: P2.
Also committed: `83d63d0` (JdbcClient as the persistence default in CLAUDE.md), `43f9957` (explicit `spring-boot-starter-jdbc`). P2 done and committed: `EditExpenseUseCase`, `EditExpenseCommand` (null = unchanged, user and expense ids required), `ExpenseNotFoundException` in the public API; `EditExpenseService` loads the owned expense, applies the changes through the domain (currency kept from the expense), checks the category and stores. The result reuses `RegisteredExpense` (Javadoc widened to "registered or edited"). Full suite: 229 tests, 0 failures (2 opt-in skipped). Next: P3.
