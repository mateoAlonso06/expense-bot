package com.expensebot.conversation.eval;

import com.expensebot.conversation.application.EvalCategoryResolver;
import com.expensebot.conversation.application.ExpenseExtractor;
import com.expensebot.conversation.application.Extraction;
import com.expensebot.conversation.infrastructure.llm.IntentClassifier;
import com.expensebot.conversation.infrastructure.llm.SpringAiExpenseExtractor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure.JdbcChatMemoryRepositoryAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real extractor (real OpenAI calls, two per case: intent, then reading) over the labeled dataset and writes a report. It never
 * fails on a low score: it measures, a human decides. Opt-in because it costs tokens:
 * <pre>
 * RUN_LLM_TESTS=true ./mvnw test -Dtest=ExtractionEval
 * </pre>
 * The report goes to {@code target/eval/<run>/}. With {@code EVAL_RECORD=true} the run is also saved under
 * {@code eval/extraction/runs/<run>/} and added to {@code eval/extraction/HISTORY.md}; {@code EVAL_NOTE} says why.
 * <p>
 * Only Spring AI and the extractor are started (no database, no Docker), so the run measures nothing but the model and
 * the prompt.
 */
@SpringBootTest(classes = ExtractionEval.EvalConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Tag("llm")
@EnabledIfEnvironmentVariable(named = "RUN_LLM_TESTS", matches = "true")
class ExtractionEval {

    private static final Path EVAL_DIR = Path.of("eval/extraction");
    private static final Path DATASET = EVAL_DIR.resolve("dataset.jsonl");
    // Every prompt and the routing between them live in this package: a change to any file is a different extractor.
    private static final Path EXTRACTOR_PACKAGE = Path.of("src/main/java/com/expensebot/conversation/infrastructure/llm");

    // No database: JPA and Flyway back off without a DataSource; Spring AI's JDBC chat memory does not, so it is
    // excluded too (the extractor does not use chat memory).
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, JdbcChatMemoryRepositoryAutoConfiguration.class})
    @Import({SpringAiExpenseExtractor.class, IntentClassifier.class})
    static class EvalConfig {
    }

    @Autowired
    private ExpenseExtractor extractor;
    @Autowired
    private ChatModel chatModel;

    @Test
    void runsTheDatasetAndWritesTheReport() throws IOException {
        ExtractionDataset dataset = ExtractionDataset.load(DATASET);
        OffsetDateTime startedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        List<ExtractionOutcome> outcomes = new ArrayList<>();
        for (ExtractionCase c : dataset.cases()) {
            long start = System.nanoTime();
            Extraction extraction = extractor.extract(c.message(), c.offeredCategories());
            long millis = (System.nanoTime() - start) / 1_000_000;
            String resolved = ExtractionReport.modelCategory(extraction)
                    .map(name -> EvalCategoryResolver.resolve(name, c.offeredCategories(), ExtractionCase.OTHER))
                    .orElse(extraction instanceof Extraction.Unreadable ? null : ExtractionCase.OTHER);
            outcomes.add(new ExtractionOutcome(c, extraction, resolved, millis));
        }

        // The options the model really runs with, not what application.yaml was meant to say.
        ChatOptions options = chatModel.getDefaultOptions();
        ExtractionReport report = new ExtractionReport(
                new ExtractionReport.RunInfo(startedAt, options.getModel(), options.getTemperature(),
                        extractorHash(), dataset.version(), dataset.cases().size(),
                        System.getenv("EVAL_NOTE")),
                ExtractionMetrics.score(outcomes),
                outcomes);

        Path target = Path.of("target/eval", report.run().id());
        report.writeTo(target);
        System.out.println(report.summary() + "  report:         " + target.resolve("report.md"));

        if ("true".equals(System.getenv("EVAL_RECORD"))) {
            Path recorded = EVAL_DIR.resolve("runs").resolve(report.run().id());
            report.writeTo(recorded);
            report.appendTo(EVAL_DIR.resolve("HISTORY.md"), "runs/" + report.run().id() + "/report.md");
            System.out.println("  recorded:       " + recorded);
        }

        assertThat(outcomes).hasSameSizeAs(dataset.cases());
    }

    /** One hash over every source file of the extractor package, in name order so it is stable. */
    private static String extractorHash() throws IOException {
        ByteArrayOutputStream sources = new ByteArrayOutputStream();
        try (Stream<Path> files = Files.list(EXTRACTOR_PACKAGE)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                sources.write(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
                sources.write(Files.readAllBytes(file));
            }
        }
        return ExtractionDataset.shortHash(sources.toByteArray());
    }
}
