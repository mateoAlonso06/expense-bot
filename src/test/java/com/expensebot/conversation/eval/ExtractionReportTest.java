package com.expensebot.conversation.eval;

import com.expensebot.conversation.application.ExpenseDraft;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ExtractionReportTest {

    private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-11-18T15:30:12-03:00");

    @TempDir
    Path dir;

    private static ExtractionReport report(String note) {
        ExtractionCase ok = new ExtractionCase("luz", "luz 100", new BigDecimal("100"), "luz", "Servicios",
                List.of(), List.of("plain"));
        ExtractionCase fallback = new ExtractionCase("osde", "osde | 120k", new BigDecimal("120000"), "osde",
                "Salud", List.of(), List.of("brand"));
        ExtractionCase none = new ExtractionCase("cafe", "tomé un café", null, null, null, List.of(),
                List.of("no-amount"));
        List<ExtractionOutcome> outcomes = List.of(
                new ExtractionOutcome(ok, new ExpenseDraft(new BigDecimal("100"), "luz", "Servicios"), "Servicios", 100),
                new ExtractionOutcome(fallback, new ExpenseDraft(new BigDecimal("120000"), "osde", "Prepaga"), "Otros", 300),
                new ExtractionOutcome(none, null, null, 200));
        return new ExtractionReport(
                new ExtractionReport.RunInfo(STARTED_AT, "gpt-4o-mini", 0.0, "3f9a1b2c", "aa11bb22", 3, note),
                ExtractionMetrics.score(outcomes), outcomes);
    }

    @Test
    void theRunIdSortsByDateAndNamesModelAndExtractor() {
        assertThat(report(null).run().id()).isEqualTo("2026-11-18_1530_gpt-4o-mini_3f9a1b2c");
    }

    @Test
    void theMarkdownShowsTheSetupTheResultsAndWhyEachFailedCaseFailed() {
        String md = report("baseline").markdown();

        assertThat(md).contains("| Model | gpt-4o-mini, temperature 0.0 |")
                .contains("`aa11bb22` (3 cases)")
                .contains("| Note | baseline |")
                .contains("| Amount | 3/3 (100.0%) |")
                .contains("| Category | 1/2 (50.0%) |")
                .contains("| Other fallback (lower is better) | 1/2 (50.0%) |")
                .contains("| brand | 1/1 (100.0%) | 0/1 (0.0%) |")
                // the pipe in the message is escaped, and the model's unknown name is shown next to the fallback
                .contains("| osde | osde \\| 120k | 120000 · Salud | 120000 · Otros (model: \"Prepaga\") |")
                .contains("avg 200 ms, max 300 ms");
    }

    @Test
    void theJsonHasTheRunTheResultsAndEveryCase() {
        JsonNode json = new JsonMapper().readTree(report(null).json());

        assertThat(json.at("/run/datasetVersion").asString()).isEqualTo("aa11bb22");
        assertThat(json.at("/run/note").isNull()).isTrue();
        assertThat(json.at("/results/category/hits").asInt()).isEqualTo(1);
        assertThat(json.at("/results/category/percent").asDouble()).isEqualTo(50.0);
        assertThat(json.at("/byTag/no-amount/category/percent").isNull()).isTrue();
        assertThat(json.at("/cases/1/actual/modelCategory").asString()).isEqualTo("Prepaga");
        assertThat(json.at("/cases/1/actual/resolvedCategory").asString()).isEqualTo("Otros");
        assertThat(json.at("/cases/2/categoryCorrect").isNull()).isTrue();
    }

    @Test
    void writesBothFiles() throws IOException {
        report(null).writeTo(dir.resolve("run"));

        assertThat(dir.resolve("run/report.md")).exists();
        assertThat(dir.resolve("run/report.json")).exists();
    }

    @Test
    void theHistoryGetsItsHeaderOnceAndOneRowPerRun() throws IOException {
        Path history = dir.resolve("HISTORY.md");

        report("baseline").appendTo(history, "runs/a/report.md");
        report("after prompt change").appendTo(history, "runs/b/report.md");

        List<String> lines = Files.readAllLines(history);
        assertThat(lines).filteredOn(l -> l.startsWith("# ")).hasSize(1);
        assertThat(lines).filteredOn(l -> l.startsWith("| [")).hasSize(2);
        assertThat(lines.getLast()).isEqualTo("| [2026-11-18_1530_gpt-4o-mini_3f9a1b2c](runs/b/report.md) | "
                + "gpt-4o-mini | 0.0 | `3f9a1b2c` | `aa11bb22` (3) | 100.0% | 50.0% | 50.0% | 200 ms | "
                + "after prompt change |");
    }
}
