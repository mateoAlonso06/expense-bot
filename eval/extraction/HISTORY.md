# Extraction eval history

One row per recorded run (`EVAL_RECORD=true`), oldest first. Compare rows only when the dataset version is the same: a new or changed case changes it.

| Run | Model | Temperature | Extractor | Dataset | Amount | Category | Other fallback | Avg latency | Note |
|---|---|---|---|---|---|---|---|---|---|
| [2026-11-18_1605_gpt-4o-mini_c6912c6a](runs/2026-11-18_1605_gpt-4o-mini_c6912c6a/report.md) | gpt-4o-mini | 0.0 | `c6912c6a` | `b6ee3327` (37) | 100.0% | 88.2% | 6.5% | 1108 ms | Baseline before #11 and #13 |
| [2026-11-19_1230_gpt-4o-mini_14e411ba](runs/2026-11-19_1230_gpt-4o-mini_14e411ba/report.md) | gpt-4o-mini | 0.0 | `14e411ba` | `1b966855` (46) | 100.0% | 86.4% | 4.9% | 1127 ms | #11: missing and ambiguous amounts in prompt and dataset |
| [2026-11-23_1535_gpt-4o-mini_0f27b976](runs/2026-11-23_1535_gpt-4o-mini_0f27b976/report.md) | gpt-4o-mini | 0.0 | `0f27b976` | `f438a41b` (54) | 85.2% | 83.0% | 6.8% | 1442 ms | #13: correction cases and prompt rules |
| [2026-11-23_1805_gpt-4o-mini_66379635](runs/2026-11-23_1805_gpt-4o-mini_66379635/report.md) | gpt-4o-mini | 0.0 | `66379635` | `f438a41b` (54) | 96.3% | 87.2% | 4.5% | 2121 ms | P3: intent classifier + focused prompts |
| [2026-11-23_1939_gpt-4o-mini_bd8cc4e4](runs/2026-11-23_1939_gpt-4o-mini_bd8cc4e4/report.md) | gpt-4o-mini | 0.0 | `bd8cc4e4` | `f438a41b` (54) | 100.0% | 85.1% | 4.5% | 1900 ms | P3: reference never goes in the correction description |
