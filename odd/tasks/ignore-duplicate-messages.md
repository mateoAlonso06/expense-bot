# Feature: ignore-duplicate-messages (issue #8)

## Objective
Process each incoming message once: the same `(channel, eventId)` delivered twice, even concurrently, reaches the conversation only the first time, and the duplicate is acknowledged so the platform stops retrying.

## Constraints
- Idempotency is enforced by the database: table `processed_message` with `PRIMARY KEY (channel, event_id)` (see `docs/diagrams/01-entity-relationship.md`). No check-then-insert in code.
- Owned by `channels` (docs/diagrams/03-modules.md): the dedupe row is recorded before the message is handed to `conversation`. Applies to both Telegram modes (webhook and polling) and to future channels.
- `event_id` is the channel-wide delivery id (`update_id` for Telegram), never the per-chat `message_id`.
- A duplicate is acknowledged: the webhook answers 200, the poller moves its offset on.
- Semantics are at-most-once: the row is recorded first, so a message whose processing fails is logged and not retried (retrying would need the row to be written in the same transaction as the expense; out of scope here).
- Assistant writes the code in small reviewable phases; user reviews each phase before the next one starts.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).
- Engram mirror: topic `odd/ignore-duplicate-messages/tasks`.

## Design
- `channels.application.ProcessedMessageRepository` (port): `boolean registerIfNew(Channel channel, String eventId)`; true only for the first registration.
- `channels.infrastructure.persistence` adapter on `JdbcClient` with `INSERT ... ON CONFLICT (channel, event_id) DO NOTHING` (affected rows tell new from duplicate). No JPA entity: nothing reads the table through JPA and a composite key would only add boilerplate.
- `channels.application.IncomingMessageGateway`: single entry point for every channel adapter (`receive(IncomingMessage)`); skips duplicates and otherwise calls `HandleIncomingMessageUseCase`. Webhook controller and poller depend on it instead of on the use case.

## Tasks
- [x] T1 Migration `V3__create_processed_message_table.sql` + `ProcessedMessageRepository` port + JDBC adapter + `@DataJpaTest` with Testcontainers (first registration, repeated registration, same event id on another channel, concurrent registrations: exactly one wins).
- [ ] T2 `IncomingMessageGateway` + unit tests; webhook controller and poller use it (their tests are updated).
- [ ] T3 Flow test: the same update posted twice to the webhook gets two 200 and one "Recibido" reply. Docs updated (modules diagram, README if it mentions the flow).

## Acceptance criteria (from #8)
- The same `(channel, eventId)` delivered twice is processed once.
- Concurrent deliveries of the same message: only one is processed (unique constraint in the database).
- The duplicate is acknowledged so the platform stops retrying.

## Open points
- At-most-once semantics (see Constraints): a crash between recording the row and finishing the work loses that message. Acceptable until messages produce stored expenses; revisit with #10.
- Rows are never purged. Fine at this scale; a retention job is out of scope.

## Progress / next step
Branch `feat/ignore-duplicate-messages` created from `main` (after PR #33). T1 done, not committed: migration V3, `ProcessedMessageRepository` port, `ProcessedMessagePersistenceAdapter` (JdbcClient) and its test (4 tests incl. 8 concurrent deliveries: exactly one wins); `ArchitectureTest` still green. Next: user review of T1, then T2.
