# Feature: telegram-inbound-port (issue #6)

## Objective
Receive Telegram messages through a channel-agnostic inbound port: the domain never sees Telegram types; polling (dev) and webhook (prod) are selectable by configuration.

## Constraints
- The port lives in the public API of `conversation` (`HandleIncomingMessageUseCase`, `IncomingMessage`); the Telegram adapter lives in `channels.infrastructure` and is the only place that knows Telegram types.
- `eventId` is the channel-wide delivery id (Telegram `update_id`), needed by #8.
- Never both polling and webhook at once.
- Assistant writes the code in small reviewable phases; user reviews each phase before the next one starts.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).

## Tasks
- [x] T1 Public API in `conversation` (`Channel`, `Attachment`, `IncomingMessage`, `HandleIncomingMessageUseCase`) + placeholder service that logs + unit test of `IncomingMessage` invariants.
- [x] T2 Telegram update DTOs + mapper to `IncomingMessage` (pure unit test; text first, attachments minimal).
- [ ] T3 Webhook endpoint (secret token header) calling the port; security rule so Telegram can reach it without a JWT.
- [ ] T4 Long polling runner calling the same mapper and port.
- [ ] T5 Configuration to choose polling or webhook (registers/removes the webhook on startup); ArchUnit rule: no Telegram types outside the adapter package.
- [ ] T6 Update docs; close issue #6.

## Acceptance criteria (from #6)
- Inbound port receives a generic message (channel, external user id, message id, text, attachments).
- Telegram adapter converts updates to it; long polling (dev) and webhook (prod) selectable by configuration.
- No Telegram type appears outside the adapter package.
- Switching between polling and webhook needs no manual steps.

## Progress / next step
T1 done. Decisions: `Channel` enum lives in the `conversation` public API (channels already depends on conversation); `Attachment` carries downloaded bytes so no Telegram `file_id` leaks.
