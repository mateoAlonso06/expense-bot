# Extraction eval history

One row per recorded run (`EVAL_RECORD=true`), oldest first. Compare rows only when the dataset version is the same: a new or changed case changes it.

| Run | Model | Temperature | Extractor | Dataset | Amount | Category | Other fallback | Avg latency | Note |
|---|---|---|---|---|---|---|---|---|---|
| [2026-11-18_1605_gpt-4o-mini_c6912c6a](runs/2026-11-18_1605_gpt-4o-mini_c6912c6a/report.md) | gpt-4o-mini | 0.0 | `c6912c6a` | `b6ee3327` (37) | 100.0% | 88.2% | 6.5% | 1108 ms | Baseline before #11 and #13 |
