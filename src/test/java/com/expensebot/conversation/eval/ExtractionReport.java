package com.expensebot.conversation.eval;

import com.expensebot.conversation.application.ExpenseDraft;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Writes one eval run as {@code report.md} (to read) and {@code report.json} (to compare with code), and appends its
 * summary row to a history table.
 */
public record ExtractionReport(RunInfo run, ExtractionMetrics metrics, List<ExtractionOutcome> outcomes) {

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    private static final DateTimeFormatter RUN_ID_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    /**
     * What a run was measured with. Two runs are comparable only with the same dataset version.
     *
     * @param extractorVersion first 8 hex of the SHA-256 of the extractor source: changes with the prompt or the
     *                         response schema (and, as a false positive, with a comment)
     * @param note             free text explaining why the run was recorded; may be null
     */
    public record RunInfo(OffsetDateTime startedAt, String model, Double temperature, String extractorVersion,
                          String datasetVersion, int cases, String note) {

        /** Sorts by date: {@code 2026-11-18_1530_gpt-4o-mini_3f9a1b2c}. */
        public String id() {
            String safeModel = model.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.-]+", "-");
            return startedAt.format(RUN_ID_TIME) + "_" + safeModel + "_" + extractorVersion;
        }
    }

    /** Writes {@code report.md} and {@code report.json} into {@code dir}, creating it if needed. */
    public void writeTo(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("report.md"), markdown());
        Files.writeString(dir.resolve("report.json"), json());
    }

    /**
     * Appends this run's row to the history table, creating the file with its header the first time.
     *
     * @param reportLink link to this run's report, relative to the history file
     */
    public void appendTo(Path history, String reportLink) throws IOException {
        if (Files.notExists(history)) {
            Files.writeString(history, """
                    # Extraction eval history

                    One row per recorded run (`EVAL_RECORD=true`), oldest first. Compare rows only when the dataset \
                    version is the same: a new or changed case changes it.

                    | Run | Model | Temperature | Extractor | Dataset | Amount | Category | Other fallback | Avg latency | Note |
                    |---|---|---|---|---|---|---|---|---|---|
                    """);
        }
        String row = "| [%s](%s) | %s | %s | `%s` | `%s` (%d) | %s | %s | %s | %d ms | %s |%n".formatted(
                run.id(), reportLink, run.model(), run.temperature(), run.extractorVersion(), run.datasetVersion(),
                run.cases(), percent(metrics.amount()), percent(metrics.category()),
                percent(metrics.otherFallback()), averageLatency(), cell(run.note() == null ? "" : run.note()));
        Files.writeString(history, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    /** The overall numbers, short enough to print in the console. */
    public String summary() {
        return """
                Extraction eval %s
                  amount:         %s
                  category:       %s
                  other fallback: %s (lower is better)
                  latency:        avg %d ms, max %d ms
                """.formatted(run.id(), metrics.amount(), metrics.category(), metrics.otherFallback(),
                averageLatency(), maxLatency());
    }

    public String markdown() {
        StringBuilder md = new StringBuilder();
        md.append("# Extraction eval: ").append(run.id()).append("\n\n");
        md.append("| | |\n|---|---|\n");
        md.append("| Date | ").append(run.startedAt()).append(" |\n");
        md.append("| Model | ").append(run.model()).append(", temperature ").append(run.temperature()).append(" |\n");
        md.append("| Extractor version | `").append(run.extractorVersion())
                .append("` (SHA-256 of `SpringAiExpenseExtractor.java`: prompt and response schema) |\n");
        md.append("| Dataset version | `").append(run.datasetVersion()).append("` (").append(run.cases())
                .append(" cases) |\n");
        if (run.note() != null) {
            md.append("| Note | ").append(cell(run.note())).append(" |\n");
        }

        md.append("\n## Results\n\n| Metric | Result |\n|---|---|\n");
        md.append("| Amount | ").append(metrics.amount()).append(" |\n");
        md.append("| Category | ").append(metrics.category()).append(" |\n");
        md.append("| Other fallback (lower is better) | ").append(metrics.otherFallback()).append(" |\n");
        md.append("| Latency | avg ").append(averageLatency()).append(" ms, max ").append(maxLatency())
                .append(" ms |\n\n");
        md.append("- **Amount**: the right amount, or no expense when the message has none, over every case.\n");
        md.append("- **Category**: the category the user ends up with (the model's answer resolved like the app ")
                .append("does; unknown names become `Otros`), over the cases that should produce an expense. ")
                .append("A missed expense counts as a wrong category.\n");
        md.append("- **Other fallback**: cases that got `Otros` although they expect another category.\n");
        md.append("- With temperature 0 the model is almost, not fully, deterministic: two identical runs can ")
                .append("differ by a case or two.\n");

        md.append("\n## By tag\n\n| Tag | Amount | Category |\n|---|---|---|\n");
        metrics.byTag().forEach((tag, m) ->
                md.append("| ").append(tag).append(" | ").append(m.amount()).append(" | ").append(m.category())
                        .append(" |\n"));

        md.append("\n## Failed cases\n\n");
        if (metrics.failures().isEmpty()) {
            md.append("None.\n");
        } else {
            md.append("| Case | Message | Expected | Got |\n|---|---|---|---|\n");
            metrics.failures().forEach(o -> md.append("| ").append(o.expected().id()).append(" | ")
                    .append(cell(o.expected().message())).append(" | ").append(expected(o)).append(" | ")
                    .append(got(o)).append(" |\n"));
        }

        md.append("\n## All cases\n\n<details>\n<summary>").append(outcomes.size())
                .append(" cases</summary>\n\n| | Case | Message | Amount | Category | Description | ms |\n")
                .append("|---|---|---|---|---|---|---|\n");
        outcomes.forEach(o -> md.append("| ").append(o.failed() ? "✗" : "✓").append(" | ")
                .append(o.expected().id()).append(" | ").append(cell(o.expected().message())).append(" | ")
                .append(o.actual() == null ? "-" : o.actual().amount().toPlainString()).append(" | ")
                .append(o.resolvedCategory() == null ? "-" : o.resolvedCategory()).append(" | ")
                .append(o.actual() == null || o.actual().description() == null ? "-"
                        : cell(o.actual().description())).append(" | ")
                .append(o.latencyMillis()).append(" |\n"));
        md.append("\n</details>\n");
        return md.toString();
    }

    public String json() {
        Map<String, Object> root = new LinkedHashMap<>();
        // LinkedHashMap, not Map.of: keeps the order and allows nulls (temperature, note)
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", run.id());
        info.put("startedAt", run.startedAt().toString());
        info.put("model", run.model());
        info.put("temperature", run.temperature());
        info.put("extractorVersion", run.extractorVersion());
        info.put("datasetVersion", run.datasetVersion());
        info.put("cases", run.cases());
        info.put("note", run.note());
        root.put("run", info);
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("amount", rate(metrics.amount()));
        results.put("category", rate(metrics.category()));
        results.put("otherFallback", rate(metrics.otherFallback()));
        results.put("latencyMillis", Map.of("avg", averageLatency(), "max", maxLatency()));
        root.put("results", results);
        Map<String, Object> byTag = new LinkedHashMap<>();
        metrics.byTag().forEach((tag, m) -> byTag.put(tag,
                Map.of("amount", rate(m.amount()), "category", rate(m.category()))));
        root.put("byTag", byTag);
        root.put("cases", outcomes.stream().map(ExtractionReport::caseJson).toList());
        return JSON.writeValueAsString(root);
    }

    private static Map<String, Object> caseJson(ExtractionOutcome o) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", o.expected().id());
        c.put("message", o.expected().message());
        c.put("tags", o.expected().tags());
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("amount", o.expected().amount() == null ? null : o.expected().amount().toPlainString());
        expected.put("description", o.expected().description());
        expected.put("category", o.expected().category());
        c.put("expected", expected);
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("amount", value(o.actual(), d -> d.amount().toPlainString()));
        actual.put("description", value(o.actual(), ExpenseDraft::description));
        actual.put("modelCategory", value(o.actual(), ExpenseDraft::categoryName));
        actual.put("resolvedCategory", o.resolvedCategory());
        c.put("actual", actual);
        c.put("amountCorrect", o.amountCorrect());
        c.put("categoryCorrect", o.expected().expectsExpense() ? o.categoryCorrect() : null);
        c.put("latencyMillis", o.latencyMillis());
        return c;
    }

    private static Map<String, Object> rate(ExtractionMetrics.Rate rate) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("hits", rate.hits());
        r.put("total", rate.total());
        r.put("percent", rate.percent());
        return r;
    }

    private static <T> T value(ExpenseDraft draft, Function<ExpenseDraft, T> field) {
        return draft == null ? null : field.apply(draft);
    }

    private static String percent(ExtractionMetrics.Rate rate) {
        return rate.percent() == null ? "n/a" : rate.percent() + "%";
    }

    private static String expected(ExtractionOutcome o) {
        if (!o.expected().expectsExpense()) {
            return "no expense";
        }
        return o.expected().amount().toPlainString() + " · " + o.expected().category();
    }

    private static String got(ExtractionOutcome o) {
        if (o.actual() == null) {
            return "no expense";
        }
        String got = o.actual().amount().toPlainString() + " · " + o.resolvedCategory();
        String modelCategory = o.actual().categoryName();
        if (modelCategory == null) {
            return got + " (model: no category)";
        }
        return modelCategory.equals(o.resolvedCategory()) ? got : got + " (model: \"" + cell(modelCategory) + "\")";
    }

    private long averageLatency() {
        return Math.round(outcomes.stream().mapToLong(ExtractionOutcome::latencyMillis).average().orElse(0));
    }

    private long maxLatency() {
        return outcomes.stream().mapToLong(ExtractionOutcome::latencyMillis).max().orElse(0);
    }

    /** Keeps user text from breaking a Markdown table. */
    private static String cell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
