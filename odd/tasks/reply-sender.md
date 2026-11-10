# Feature: reply-sender (issue #7)

## Objective
Reply to users through a channel-agnostic outbound port: the domain never sees Telegram types, and a failed send is logged without losing the stored expense.

## Constraints
- The port (`ReplySender`) lives in the public API of `conversation` and is implemented by `channels` (channels already depends on conversation).
- The sender is resolved by the message's originating channel (`Channel`).
- A sending failure is logged and never propagates to the caller.
- Assistant writes the code in small reviewable phases; user reviews each phase before the next one starts.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).
- Order keeps the Spring context green at every phase: `ReplyService` injects `List<ReplySender>` (fails with no beans), so it comes after the Telegram sender exists.
- Engram mirror: topic `odd/reply-sender/tasks`.

## Tasks
- [x] T1 `TelegramApi.sendMessage(chatId, text)` (POST JSON, plain text, errors never leak the bot token) + test with a mock server.
- [x] T2 `conversation`: `ReplySender` port (`channel()`, `send(externalUserId, text)`) + `TelegramReplySender` in `channels` (always on, both modes) + test.
- [x] T3 `conversation`: internal `ReplyService` picks the sender by channel and logs failures without propagating. Unit tests.
- [x] T4 Placeholder: `HandleIncomingMessageService` replies with the fixed text "Recibido" through `ReplyService` (user's request, so the full loop can be tried with the real bot). Temporary: removed when the agent (#10) lands. Includes the first flow test: a `@SpringBootTest` that posts an update to the webhook and checks that a `sendMessage` with "Recibido" goes out (Telegram simulated with `MockRestServiceServer`).

## Acceptance criteria (from #7)
- An outbound port sends a reply given a channel and an external user id.
- The Telegram adapter implements it; the sender is resolved by the message's originating channel.
- Sending failures are logged and do not lose the stored expense.

## Open points
- Telegram's docs do not state that `from.id` equals `chat.id` in a private chat (checked in `User` and `Chat`). The adapter will use the external user id as `chat_id`; confirm with the real bot.
- `sendMessage` text is limited to 1-4096 characters; Telegram allows about one message per second per chat (429 otherwise). Splitting and retry are out of scope.

## Progress / next step
Branch `feat/reply-sender` created from `main` (after PR #32). T1 done and committed (`chat_id` is sent as a number, no `parse_mode`). T2 done and committed: `ReplySender` port + `TelegramReplySender`. T3 done and committed: `ReplyService`. T4 done: `HandleIncomingMessageService` replies "Recibido" (unit test) and `TelegramReplyFlowTest` (2 tests, local HTTP server standing in for Telegram). Full suite: 83 tests, 0 failures. Next: user review, then PR with `Closes #7`. To try with the real bot: `TELEGRAM_MODE=polling` + `TELEGRAM_BOT_TOKEN`, and confirm that the user id works as `chat_id`.
