# expense-bot

Expense-tracking bot. Users log expenses through Telegram (later WhatsApp) using natural language, receipt photos or voice notes. An AI agent extracts and categorizes them. A web panel (separate SPA) shows history, dashboards and limits. Portfolio project aimed at Backend + AI Engineering roles.

Tracker: GitHub issues + project board `bills bot kanban` in `mateoAlonso06/bills-bot-spring` (27 issues, milestones MVP / v1 / v2). The issues hold the requirements and acceptance criteria; read the issue before implementing it.

## Working rules (highest priority)

- The assistant writes the code, **in small reviewable phases** (like a well-scoped PR). Each phase is a coherent slice (one use case, one adapter, one migration, one config block) with its tests, small enough to read in one sitting (aim for roughly 200-400 changed lines, never a whole feature at once).
- After each phase: stop, summarize what changed and why (short), point out the key concepts to review (especially Spring / Spring AI usage), and wait for the user's go-ahead before starting the next phase. Do not commit until the user approves the phase.
- The user is still learning many concepts used here. Explain any new Spring / Spring AI / security concept the first time it appears, briefly.
- Keep explanations of implementations and configuration short and simple. Go longer only when the case genuinely needs more detail.
- Reply in the user's language (Spanish). Code, comments, docs, issues, tests and commits are in English.
- Conventional commits. Never add `Co-Authored-By` or any AI attribution to commits or PRs.
- Verify technical claims before stating them; say when something is unverified.

## Stack

Java 21, Spring Boot 4.1.x, Maven (`./mvnw`), Spring AI 2.0.x (OpenAI; JDBC chat memory), PostgreSQL + Flyway, Spring Security as an OAuth2 resource server, Actuator + Micrometer/Prometheus + OpenTelemetry, springdoc, Lombok, Testcontainers, ArchUnit.

The shell defaults to JDK 17. Use JDK 21 (SDKMAN) for builds:

```bash
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.2-tem PATH=$JAVA_HOME/bin:$PATH
./mvnw -Dtest=ArchitectureTest test
```

## Architecture

Modular monolith, package by feature under `com.expensebot`, hexagonal inside each module:

`accounts` (profile, channel identities, link codes) · `expenses` (expenses + categories) · `conversation` (agent orchestration, memory) · `budgets` (limits, v1) · `channels` (Telegram/WhatsApp adapters).

Each module has `domain`, `application`, `infrastructure`.
- `domain`: plain Java. No Spring, Jakarta or Hibernate.
- `application`: use cases and ports. Never depends on `infrastructure`.
- The module's base package is its public API (use-case interfaces, small records, events). Other modules may only use that, never another module's `domain`, `application` or `infrastructure`.
- Reference other modules' aggregates by id, not by object. If data is needed, the owner module exposes a small read-only record.
- `ArchitectureTest` enforces these rules and runs with `./mvnw test`. Do not weaken a rule to make code compile; fix the dependency.
- Add a port only when there is more than one implementation or a real need to fake it (channel, LLM, persistence). No speculative interfaces, no `shared`/`common` package.

## Development approach

- Outside-in: write the use case (and its test) first; the domain entity grows from what the use case needs. Review invariants after each use case.
- Domain model and persistence model are separate. Audit columns (`created_at`, `updated_at`) belong to persistence, not the domain.
- Order of work follows the `Depends on` links in the issues. A data-model issue precedes the first table-creating issue.

## Decisions already made

- Auth: Keycloak is the identity provider (local login + Google as a Keycloak identity provider). The SPA logs in via Authorization Code + PKCE; the API only validates JWTs (`issuer-uri`). No `oauth2-client` in this app.
- Users live in Keycloak; the app keeps a `user_profile` keyed by the token `sub`.
- Channels: Telegram first (long polling in dev, webhook in prod, never both at once). The domain never sees channel-specific types. Identities stored as `(channel, external_id)`, unique.
- Linking: the web user requests a single-use, short-TTL code (stored hashed); sending it to the bot creates the `channel_identity` from the sender id in the message.
- Idempotency: unique `(channel, message_id)`.
- Categories: global base catalog seeded by Flyway (`user_id` null). Category names are user-facing data and are seeded in Spanish (`Alimentación`, `Vivienda`, `Transporte`, `Salud`, `Ocio`, `Servicios`, `Otros`). In code, docs and issues the fallback category is still called `Other`; its seeded display name is `Otros`. It is a system category (never deletable or renamable) and is always looked up by its stable id `00000000-0000-0000-0000-000000000001`, never by name. Users never have to provide a category; the agent infers it and falls back to `Other`. Custom categories are v2.
- Expenses: amount `NUMERIC(19,2)` / `BigDecimal`, explicit currency (ARS). Only amount, description and category are editable; date, source channel and original message are immutable.
- Only the amount is mandatory to extract; a low-confidence category never triggers a question.

## Local environment

`compose.yaml` runs Postgres (Keycloak is added in issue #2). Secrets come from environment variables and are never committed.
