# Feature: expense-categories (issue #9)

## Objective
Seed a base catalog of expense categories, with a system category `Other`, and expose an endpoint that lists them.

## Constraints
- Module `expenses`, hexagonal: domain (plain Java) / application / infrastructure. Public API in the module base package.
- Outside-in: use case and its test first. Domain and persistence models are separate.
- User writes the code by hand; assistant explains and reviews.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).

## Tasks
- [x] T1 Domain `Category` (id, name, system flag); `Other` has a stable id and is never renamable or deletable.
- [x] T2 Use case `ListCategories` + port `CategoryRepository` + unit test with a fake port.
- [x] T3 Flyway `V1`: `category` table (`user_id` null for global) + seed catalog incl. `Other`.
- [x] T4 Persistence adapter (JPA entity separate from the domain) implementing the port.
- [ ] T5 REST endpoint listing categories (controller written; security rule and Testcontainers integration test pending).
- [ ] T6 Logging where relevant; update/close issue #9.

## Acceptance criteria (from #9)
- Flyway seeds a base catalog (Food, Housing, Transport, Health, Leisure, Services).
- Catalog includes system category `Other` (not deletable/renamable, exists for every user).
- An endpoint lists the available categories.

## Progress / next step
T1-T4 done (unit tests, ArchitectureTest pass; migration validated in a rolled-back transaction). JPA entity has audit timestamps. Not yet exercised against the DB from the app.
Next: T5 (security rule for /api/categories + integration test), then T6.
