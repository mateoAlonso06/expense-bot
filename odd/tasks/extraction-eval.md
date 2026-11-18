# Feature: extraction-eval (issue #14)

## Objective
Measure how well the LLM extractor reads amount and category from chat messages, with a versioned labeled dataset and one command that prints and stores a report. The numbers become the baseline that prompt or model changes (#11, #13) are compared against.

## Problem / why
Today the only check is an opt-in test with 14 messages that asserts the amount and prints the category for a human to read. There is no number to compare before and after a change, so a prompt tweak that fixes one case and breaks three goes unnoticed.

## Scope
- Dataset `eval/extraction/dataset.jsonl` (at least 30 labeled cases, with tags per kind of difficulty).
- Scoring in plain Java (test code): amount accuracy, category accuracy, `Other` fallback rate when another category was expected, per-tag breakdown, failed cases.
- Runner: opt-in command that calls the real extractor, writes `report.md` + `report.json` to `target/eval/`, and with `EVAL_RECORD=true` saves the run under `eval/extraction/runs/<date>_<model>_<prompt-hash>/` and appends a row to `eval/extraction/HISTORY.md`.
- Baseline run recorded in the repo. The old `OpenAiExpenseExtractionTest` is replaced (its 14 cases move into the dataset).
- Out of scope: LLM-as-judge for descriptions, CI runs, eval platforms (Langfuse etc.), multi-amount messages (behavior defined by #11).

## Decisions
- Category is scored as the user gets it: the model's name resolved the same way the app does (`CategoryMatcher`: case and accents ignored), and anything unresolved counts as `Otros`. So every case with an amount has an expected category (a message that says nothing about what was bought expects `Otros`).
- Category accuracy uses every case that should produce an expense; a missed expense counts as a wrong category too.
- Descriptions are labeled but not scored (free text; exact match would be unfair). They are shown per case in the report.
- Reports are files in the repo (Markdown to read, JSON to compare); history is an index table, run folders sort by ISO date.

## Constraints
- Eval code lives in the test tree (tooling, not production). `ArchitectureTest` ignores test classes.
- Runs cost OpenAI tokens: opt-in only, never part of `./mvnw test`.
- TDD: not configured (ordinary functional checks). Runner: `./mvnw test` (JDK 21).

## Tasks
- [x] P1 Dataset + scoring: `dataset.jsonl` (>= 30 cases), loader with validation and dataset version (hash + count), scorer and metrics, unit tests (scoring on fake outcomes; the real dataset loads and is valid).
- [x] P2 Runner + reports + baseline: opt-in runner over the real extractor, `report.md` / `report.json`, `EVAL_RECORD` + `HISTORY.md`, model / temperature / prompt hash recorded, README section, remove `OpenAiExpenseExtractionTest`, record the baseline run.

## Acceptance criteria (from #14)
- A versioned dataset of at least 30 labeled messages (amount, description, category) lives in the repo.
- One command runs the dataset and prints a report.
- The report gives accuracy per field: amount, category, and how often `Other` is used when a category was expected.
- Results are reproducible (fixed model/params recorded in the report).

## Progress / next step
Branch `feat/extraction-eval` from `main` (`51fa856`).
P1 done (not committed, waiting for review): `eval/extraction/dataset.jsonl` with 37 cases (the 14 old ones plus 23 new: brands, slang such as "medio palo" / "4.5k" / "1,5 lucas", amounts in words, `Otros`-expected cases, questions and future tense without amount). Test-tree package `com.expensebot.conversation.eval`: `ExtractionCase`, `ExtractionDataset` (strict JSONL: unknown fields, unoffered categories, category without amount, duplicate ids fail with the line number; version = first 8 hex of the file SHA-256), `ExtractionOutcome` (amount compared by value; category compared on the resolved name), `ExtractionMetrics` (amount, category, Other fallback, per tag, failures). 17 unit tests. Full suite: 259 run (257 + 2 opt-in skipped), 0 failures; docs/testing.md counts refreshed. Committed as `462b5b1`.
P2 done (not committed, waiting for review): `ExtractionEval` (opt-in, `RUN_LLM_TESTS=true ./mvnw test -Dtest=ExtractionEval`; the name does not end in `Test`, so the default suite never picks it up) starts only Spring AI + the extractor (`DataSourceAutoConfiguration` and `JdbcChatMemoryRepositoryAutoConfiguration` excluded: the JDBC chat memory does not back off without a database), reads model and temperature from `ChatModel#getDefaultOptions`, and the extractor version from the SHA-256 of `SpringAiExpenseExtractor.java`. `ExtractionReport` writes `report.md` / `report.json` and appends to `HISTORY.md`; `EvalCategoryResolver` (test tree, same package as `CategoryMatcher`) resolves categories like the app. `OpenAiExpenseExtractionTest` removed. Baseline recorded: `2026-11-18_1605_gpt-4o-mini_c6912c6a`, dataset `b6ee3327`: amount 37/37, category 30/34 (88.2%), Other fallback 2/31 (6.5%), avg 1108 ms. Failures: rappi and multa de tránsito -> Otros, arreglo del auto and reparación del techo -> Servicios. Full suite: 265 run (264 + 1 opt-in skipped), 0 failures. Next: PR closing #14.
