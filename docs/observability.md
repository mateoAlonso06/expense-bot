# Observability

Traces are pushed over OTLP to Grafana LGTM (`OTLP_ENDPOINT`). Metrics are exposed for scraping at `/actuator/prometheus`, without a token (public in dev; restrict access at deploy time).

## LLM metrics

Spring AI measures every model call and names the metrics after the [OpenTelemetry GenAI semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/):

| Metric | Type | What it measures |
|---|---|---|
| `gen_ai_client_operation_seconds` | timer (`_count`, `_sum`, `_max`) | Latency of each model call |
| `gen_ai_client_token_usage_total` | counter | Tokens reported by the provider, split by `gen_ai_token_type`: `input`, `output`, `total` |

Tags on both:

| Tag | Values |
|---|---|
| `expensebot_use_case` | `classify-intent` (expense or correction), `read-expense`, `read-correction`; `unknown` for a call without one |
| `gen_ai_request_model`, `gen_ai_response_model` | e.g. `gpt-4o-mini` |
| `gen_ai_system`, `gen_ai_operation_name` | `openai`, `chat` |
| `error` | `none`, or the exception name of a failed call |

Each user message costs two calls: `classify-intent`, then `read-expense` or `read-correction`. A new LLM call declares its use case with `.advisors(LlmUseCase.X.inRequest())`; `LlmUseCaseObservationFilter` copies it onto the metrics.

PromQL examples:

```promql
# average latency per use case, last 5 minutes
rate(gen_ai_client_operation_seconds_sum[5m]) / rate(gen_ai_client_operation_seconds_count[5m])

# input tokens per use case, last hour
sum by (expensebot_use_case) (increase(gen_ai_client_token_usage_total{gen_ai_token_type="input"}[1h]))
```

Prompt and completion content is never attached to metrics or traces.
