# Expose LLM latency and token usage metrics (#15)

## Objective
Expose the latency and the input/output tokens of every LLM call at `/actuator/prometheus`, tagged by use case, and list the metric names in the README. Last open issue of the MVP milestone.

## Problem / why
Each user message costs two model calls (classify the intent, then read an expense or a correction). Latency and tokens (cost) per call are the key signals of an AI-backed service, and today they cannot be told apart by use case.

## Scope (agreed design)
- Reuse Spring AI's own observations instead of custom meters: `gen_ai.client.operation` (timer) and `gen_ai.client.token.usage` (counter, tag `gen_ai.token.type`), named after the OpenTelemetry GenAI semantic conventions. Both take their tags from the chat model observation's low-cardinality key values.
- Add one low-cardinality tag `expensebot.use_case` with three values: `classify-intent`, `read-expense`, `read-correction`. Each call puts its use case in the `ChatClient` request context; an `ObservationFilter` copies it onto the chat model observation (read from the enclosing `ChatClient` observation).
- Plan B if P1 shows the filter cannot reach the use case: own Micrometer timer and token counters in the adapter (from `responseEntity` usage).
- Prompt and completion content is never logged or attached to traces (personal data).

## Constraints
- Small phases with review; one new concept per phase; design before code.
- Effective TDD: off (no project/session setting enables it); ordinary checks per phase. Runner: `./mvnw test` with JDK 21.

## Tasks
- [x] P1 Flow test with a fake OpenAI server (local HTTP): a real `ChatClient` call produces `gen_ai` latency and token metrics in the Prometheus scrape; check whether the chat model observation can reach the use case set on the `ChatClient` call.
- [x] P2 `expensebot.use_case` tag on the three calls and the `ObservationFilter`; tests.
- [ ] P3 README: metric names and tags.

## Acceptance criteria (from #15)
- Micrometer metrics for model latency and input/output tokens are exposed at `/actuator/prometheus`.
- Metrics are tagged by use case (e.g. text extraction).
- The README lists the metric names.

## Progress / next step
Branch `feat/llm-metrics` from `main` (`c73c0b5`).
P1 done (`LlmMetricsFlowTest`, fake OpenAI at `/v1/chat/completions`, `@AutoConfigureMetrics` because Boot tests keep metrics in memory only). Observed in the scrape: `gen_ai_client_operation_seconds` (tags `error`, `gen_ai_operation_name`, `gen_ai_request_model`, `gen_ai_response_model`, `gen_ai_system`) and `gen_ai_client_token_usage_total` (same tags plus `gen_ai_token_type` input/output/total); also `spring_ai_chat_client_seconds` and `spring_ai_advisor_seconds`. Probe finding: the parent of the chat model observation is an advisor observation (`spring.ai.advisor`, "Tool Calling Advisor"), not the `ChatClient` one, so the filter must walk up the parents until the `ChatClientObservationContext`. Full suite: 338 run (337 + 1 opt-in skipped), 0 failures.
Open point for P3: `/actuator/prometheus` requires a JWT today (only `/actuator/health` is public), so a Prometheus server could not scrape it.
P1 committed as `6edefb4`.
P2 done (not committed): `LlmUseCase` enum (tag `expensebot.use_case`, values `classify-intent`, `read-expense`, `read-correction`) put in the request context with `.advisors(a -> a.param(...))`; `LlmUseCaseObservationFilter` (`@Component`, Boot registers `ObservationFilter` beans) tags every `ChatModelObservationContext`, walking parents up to the `ChatClientObservationContext`; `unknown` when no use case (Prometheus needs the same tag names on every meter of one name). Tests: 4 filter unit tests, flow test now runs `extract` (two calls) and checks latency and tokens per use case. Full suite: 342 run (341 + 1 opt-in skipped), 0 failures.
Next: P3.
