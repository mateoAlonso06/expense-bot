# Feature: user-profile-provisioning (issue #4)

## Objective
Create the business `user_profile` on the first authenticated request, keyed by the token `sub`, with the email taken from the token.

## Constraints
- Module `accounts`, hexagonal: domain (plain Java) / application / infrastructure. Public API in the module base package.
- Outside-in: use case and its test first. Domain and persistence models are separate.
- Idempotent and race-free: one `INSERT ... ON CONFLICT DO NOTHING`, uniqueness guaranteed by the primary key.
- No cache for now (tracked in #29).
- Assistant writes the code in small reviewable phases; user reviews each phase before the next one starts.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).

## Tasks
- [x] T1 Flyway `V2`: `user_profile` table (id = `sub`, email, audit columns).
- [x] T2 Use case `ProvisionUserProfile` + port + unit test with a fake port.
- [x] T3 Persistence adapter with idempotent insert + Testcontainers test (known sub, unknown sub, concurrent).
- [x] T4 Filter that calls the use case after JWT authentication + web test. The filter parses `sub` as UUID; a missing or non-UUID `sub` (or missing email) is rejected with 401 and a clear log, never a 500. Test covers it.
- [x] T5 Update docs/diagrams (design note 4 in `03-modules.md` now records the decision); close issue #4 once the PR is merged.

## Acceptance criteria (from #4)
- Valid token with an unknown `sub` creates a profile (email from the token).
- Repeated or concurrent first requests leave exactly one profile.
- A known `sub` creates nothing.

## Progress / next step
T1 done. Decisions: `email` is `NOT NULL`; `sub` is stored as UUID (Keycloak default; realm has no custom subject mapper, unverified with a real token) with explicit rejection of non-UUID values.
T2 done. T3 done (pending review): native `INSERT ... ON CONFLICT DO NOTHING`; removing the clause makes the repeated-call and concurrent tests fail. T4 done (filter + 401 on invalid `sub`/email). T5 done. Open: verify the real Keycloak `sub` format with a token once the realm has a test user.
