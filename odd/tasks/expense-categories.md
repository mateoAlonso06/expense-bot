# Feature: expense-categories (issue #9)

## Objective
Seed a base catalog of expense categories, with a system category `Other`, and expose an endpoint that lists them.

## Constraints
- Module `expenses`, hexagonal: domain (plain Java) / application / infrastructure. Public API in the module base package.
- Outside-in: use case and its test first. Domain and persistence models are separate.
- Assistant writes the code in small reviewable phases; user reviews each phase before the next one starts.
- Category names are seeded in Spanish; `Other` is displayed as `Otros` and looked up by fixed id.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).

## Tasks
- [x] T1 Domain `Category` (id, name, system flag); `Other` has a stable id and is never renamable or deletable.
- [x] T2 Use case `ListCategories` + port `CategoryRepository` + unit test with a fake port.
- [x] T3 Flyway `V1`: `category` table (`user_id` null for global) + seed catalog incl. `Other`.
- [x] T4 Persistence adapter (JPA entity separate from the domain) implementing the port.
- [x] T5 REST endpoint listing categories (controller; protected by JWT, covered by `JwtAuthenticationTest` from #3). Hibernate `ddl-auto: validate` so Flyway is the schema source of truth; minimal persistence test (`CategoryPersistenceAdapterTest`, real Postgres via Testcontainers) checks the seed: 7 categories, `Other` as the only system one with its fixed id.
- [ ] T6 Logging where relevant; update/close issue #9.

## Acceptance criteria (from #9)
- Flyway seeds a base catalog (Alimentación, Vivienda, Transporte, Salud, Ocio, Servicios; seeded in Spanish).
- Catalog includes system category `Other` (not deletable/renamable, exists for every user).
- An endpoint lists the available categories.

## Progress / next step
T1-T5 done. Full suite green (14 tests); `validate` verified to fail startup when a mapped column is wrong. The endpoint is exercised against the DB through the persistence test, not through an end-to-end test (decided: the endpoint only delegates).
Open: the JPA entity does not map `user_id` yet, so `findAll` will need a filter when custom categories arrive (v2, #23).
Next: T6 (logging where relevant, update/close issue #9).
