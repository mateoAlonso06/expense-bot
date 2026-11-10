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
- [ ] T2 Request side: migration `link_code`, domain `LinkCode`, persistence, `RequestLinkCodeUseCase` service (generation, hashing, TTL as a property) + tests.
- [ ] T3 `POST /api/link-codes` controller + `@WebMvcTest` (authenticated 201, unauthenticated 401).
- [ ] T4 Link side: migration `channel_identity`, domain `ChannelIdentity`, persistence, `LinkChannelIdentityUseCase` service (atomic consume + create in one transaction) + tests (valid, expired, used twice, identity taken, user already linked, concurrent consumption).
- [ ] T5 `conversation` recognizes the code and replies per result; flow test (webhook -> link -> reply); docs.

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
Branch `feat/link-telegram-account` created from `main` (after PR #34). The docs fix for the ER notes is committed (`6e4f7fa`). T1 done, not committed: `Channel` moved to `com.expensebot.accounts` (`git mv`, 12 files updated plus 3 that now import it explicitly); full suite 91 tests, 0 failures, `ArchitectureTest` green. Next: user review of T1, then T2.
