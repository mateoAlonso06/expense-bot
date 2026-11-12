# Feature: link-telegram-account (issue #5)

## Objective
An authenticated web user requests a single-use, short-lived code, sends it to the bot, and the bot links the sender's channel identity to that user. Identities are stored as `(channel, externalId)` so other channels reuse the model.

## Constraints
- Module graph stays acyclic: `channels -> conversation -> accounts`. `accounts` depends on nobody, so the `Channel` enum moves to the public API of `accounts` (decided with the user, option 1). `conversation` reaches `accounts` only through its public use-case interfaces; references between modules are by id.
- Codes are stored hashed (`link_code.code_hash`, unique), never in plain text; single use through `used_at`; short TTL (10 minutes).
- Consuming a code must be atomic: one statement that sets `used_at` only if the code is unused and unexpired, so concurrent attempts cannot both succeed.
- A code is not burned when linking is rejected for a reason unrelated to the code (identity already taken): consume and create the identity in one transaction.
- `channel_identity`: `UNIQUE (channel, external_id)` and `UNIQUE (user_id, channel)` (docs/diagrams/01-entity-relationship.md). Foreign keys to `user_profile` are enforced by the database.
- Domain objects: private constructor + `create`/`restore`, no records, no Spring in `domain`. Audit columns belong to persistence.
- The code is detected deterministically in `conversation` before anything else; it never goes to an LLM.
- Assistant writes the code in small reviewable phases; user reviews each phase before the next one starts.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).
- Engram mirror: topic `odd/link-telegram-account/tasks`.

## Design
- Code: 8 characters from an unambiguous alphabet (no `0/O`, `1/I/L`), generated with `SecureRandom`, shown as `XXXX-XXXX`; input is normalized (trim, upper case, dash optional). Stored as SHA-256 hex: the code is random with high entropy, so a fast hash is enough (no salt or slow hash needed).
- `accounts` public API: `Channel`, `RequestLinkCodeUseCase` (-> record with `code` and `expiresAt`), `LinkChannelIdentityUseCase` (-> result enum: `LINKED`, `INVALID_CODE` for unknown/expired/used without telling which, `CHANNEL_ALREADY_LINKED` for an identity owned by someone else or a user who already linked that channel).
- Web: `POST /api/link-codes`, authenticated, answers 201 with `{code, expiresAt}`; the user id is the JWT `sub`.
- `conversation`: `HandleIncomingMessageService` recognizes a message that is a link code, calls `LinkChannelIdentityUseCase` and replies with a fixed text per result.

## Tasks
- [x] T1 Move `Channel` to `com.expensebot.accounts` (imports only, no behavior change). `ArchitectureTest` and the full suite stay green.
- [x] T2 Request side: migration `link_code`, domain `LinkCode`, persistence, `RequestLinkCodeUseCase` service (generation, hashing, TTL as a property) + tests.
- [x] T3 `POST /api/link-codes` controller + `@WebMvcTest` (authenticated 201, unauthenticated 401).
- [x] T4a Channel identity storage: migration `channel_identity`, domain `ChannelIdentity`, `ChannelIdentityRepository.saveIfAvailable` + persistence adapter + tests (stores, taken by another user, second account on the same channel, same user on another channel, same external id on another channel, missing user).
- [x] T4b Link use case: `LinkChannelIdentityUseCase` + result enum, atomic code consumption in `LinkCodeRepository`, service that consumes the code and creates the identity in one transaction (rolled back if the identity is taken, so the code is not burned) + tests (valid, expired, used twice, identity taken does not burn the code, user already linked, concurrent consumption).
- [x] T5a Resolve side in `accounts`: `ChannelIdentityRepository.findUserId(channel, externalId)` + adapter, public `ResolveChannelIdentityUseCase` (-> `Optional<UUID>`) + service + tests. Added to this issue because #10 assumes it ("given an unlinked user") but nobody owned it.
- [ ] T5b `conversation`: `HandleIncomingMessageService` calls `LinkChannelIdentityUseCase` first (reply per result), then `ResolveChannelIdentityUseCase` (unlinked -> linking instructions; linked -> current placeholder reply until the agent, #10); flow test (webhook -> link -> reply); docs.

## Acceptance criteria (from #5)
- Authenticated user requests a code: a single-use code with a short TTL is returned.
- A valid code sent to the bot links the identity and the bot confirms.
- An expired or already used code is rejected.
- An identity already linked to another user is rejected.
- Identities are stored as `(channel, externalId)`.

## Open points
- Brute force: no rate limit on the bot side. The space is 31^8 (about 8.5e11) and codes live 10 minutes, which makes a blind guess very unlikely, but a limit per identity is worth adding later.
- Whether to also accept Telegram's `/start <code>` deep link (the web could show `t.me/<bot>?start=<code>`). Cheap to add in T5, to decide then.
- Requesting a new code does not invalidate older unexpired ones. Acceptable; revisit if it matters.
- Expired and used rows are never purged.

## Progress / next step
Branch `feat/link-telegram-account` created from `main` (after PR #34). Committed on the branch: docs fix for the ER notes (`6e4f7fa`); T1 `Channel` moved to `com.expensebot.accounts` (`95f216c`); T2 request side (`331cf5a`): migration V4 (`link_code`), `LinkCode` domain (only `create`; `restore` when the link side needs it), `LinkCodeRepository` port + `LinkCodePersistenceAdapter` on `JdbcClient` (no JPA entity, since consuming a code will be one atomic UPDATE), `LinkCodeFormat` (generation, normalization, SHA-256; package-private, reused by T4), `RequestLinkCodeService`, public `RequestLinkCodeUseCase` + `IssuedLinkCode`, `ClockConfig` bean, `accounts.link-code.ttl: 10m`. Side work also committed: test catalog and CI suites (`docs/testing.md`, `@Tag` per category, `-Pfast` and `-Pintegration` Maven profiles), the hash pinned to a known SHA-256 value, the link code service test split into one behavior per test.
T3 committed (`b890f4a`): `LinkCodeController` (`POST /api/link-codes`, 201 with `{code, expiresAt}`; the user id is the JWT `sub` read with `@AuthenticationPrincipal Jwt`, and `UserProvisioningFilter` already guarantees the `sub` is a UUID) and `LinkCodeControllerTest` (201 with the subject reaching the use case; 401 without a token). `expiresAt` is serialized as ISO-8601 text.
T4 was split in two to keep phases small. T4a done and committed (`27d3ddc`): migration V5 (`channel_identity`, both unique constraints), `ChannelIdentity` domain (only `create`), `ChannelIdentityRepository.saveIfAvailable` (true stored, false conflict) implemented on `JdbcClient` with `INSERT ... ON CONFLICT DO NOTHING` (no target: it skips on either unique constraint; a missing user still raises through the foreign key) + 9 tests. Full suite: 116 tests, 0 failures. T4b design: consume the code first with one atomic `UPDATE ... RETURNING user_id`, then `saveIfAvailable`; on conflict the transaction is rolled back (`TransactionTemplate` + `setRollbackOnly`) so the code is not burned and the result is `CHANNEL_ALREADY_LINKED`.
Note for T4/T5: `conversation` cannot see `LinkCodeFormat`, so recognizing "this message looks like a code" must be exposed by the accounts public API (e.g. a `NOT_A_CODE` result of `LinkChannelIdentityUseCase`). T4b must test concurrent consumption with the same recipe as `ProcessedMessagePersistenceAdapterTest.letsExactlyOneConcurrentDeliveryWin` (start latch, exact winner count, no wrapping transaction). Next: user review of T4a, then T4b.
T4b done, awaiting review (not committed): `LinkChannelIdentityUseCase` + `LinkResult` (`LINKED`, `INVALID_CODE`, `CHANNEL_ALREADY_LINKED`, `NOT_A_CODE`) in the accounts public API; `LinkCodeRepository.consume` (one `UPDATE ... RETURNING user_id`); `LinkCodeFormat.looksLikeCode`; `LinkChannelIdentityService` (`TransactionTemplate`, `setRollbackOnly` when the identity conflicts so the code is not burned). Full suite: 131 tests, 0 failures. Next: T5.
T5a done, awaiting review (not committed, on top of the uncommitted T4b): `findUserId` in `ChannelIdentityRepository` + adapter, `ResolveChannelIdentityUseCase` + service. Full suite: 136 tests, 0 failures. Next: T5b.
