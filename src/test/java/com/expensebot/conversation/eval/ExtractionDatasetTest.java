package com.expensebot.conversation.eval;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class ExtractionDatasetTest {

    static final Path DATASET = Path.of("eval/extraction/dataset.jsonl");

    @TempDir
    Path dir;

    private Path file(String... lines) throws IOException {
        return Files.write(dir.resolve("dataset.jsonl"), String.join("\n", lines).getBytes());
    }

    @Test
    void theRepositoryDatasetIsValidAndLargeEnough() throws IOException {
        ExtractionDataset dataset = ExtractionDataset.load(DATASET);

        assertThat(dataset.cases()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(dataset.cases()).anyMatch(c -> !c.expectsExpense());
        assertThat(dataset.cases()).anyMatch(c -> !c.extraCategories().isEmpty());
        assertThat(dataset.cases()).allMatch(c -> !c.tags().isEmpty());
    }

    @Test
    void readsCasesAndSkipsBlankLines() throws IOException {
        ExtractionDataset dataset = ExtractionDataset.load(file(
                "{\"id\":\"a\",\"message\":\"steam 5000\",\"amount\":\"5000\",\"category\":\"Juegos\","
                        + "\"extraCategories\":[\"Juegos\"],\"tags\":[\"custom-category\"]}",
                "",
                "{\"id\":\"b\",\"message\":\"tomé un café\",\"amount\":null,\"category\":null}"));

        assertThat(dataset.cases()).hasSize(2);
        ExtractionCase first = dataset.cases().getFirst();
        assertThat(first.amount()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(first.offeredCategories()).endsWith("Juegos").contains("Otros");
        assertThat(dataset.cases().get(1).expectsExpense()).isFalse();
        assertThat(dataset.cases().get(1).tags()).isEmpty();
    }

    @Test
    void theVersionChangesWithTheContent() throws IOException {
        String line = "{\"id\":\"a\",\"message\":\"luz 100\",\"amount\":\"100\",\"category\":\"Servicios\"}";
        String first = ExtractionDataset.load(file(line)).version();
        String same = ExtractionDataset.load(file(line)).version();
        String changed = ExtractionDataset.load(file(line.replace("100\",", "200\","))).version();

        assertThat(first).hasSize(8).isEqualTo(same).isNotEqualTo(changed);
    }

    @Test
    void rejectsAMisspelledField() throws IOException {
        Path dataset = file("{\"id\":\"a\",\"message\":\"luz 100\",\"amount\":\"100\",\"catgory\":\"Servicios\"}");

        assertThatThrownBy(() -> ExtractionDataset.load(dataset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 1");
    }

    @Test
    void rejectsACategoryThatIsNotOffered() throws IOException {
        Path dataset = file("{\"id\":\"a\",\"message\":\"steam 100\",\"amount\":\"100\",\"category\":\"Juegos\"}");

        assertThatThrownBy(() -> ExtractionDataset.load(dataset))
                .hasMessageContaining("category must be one of");
    }

    @Test
    void rejectsAnExpenseWithoutCategory() throws IOException {
        Path dataset = file("{\"id\":\"a\",\"message\":\"gasté 100\",\"amount\":\"100\",\"category\":null}");

        assertThatThrownBy(() -> ExtractionDataset.load(dataset))
                .hasMessageContaining("category must be one of");
    }

    @Test
    void rejectsACategoryWithoutAmount() throws IOException {
        Path dataset = file("{\"id\":\"a\",\"message\":\"luz\",\"amount\":null,\"category\":\"Servicios\"}");

        assertThatThrownBy(() -> ExtractionDataset.load(dataset))
                .hasMessageContaining("without amount");
    }

    @Test
    void rejectsDuplicateIds() throws IOException {
        String line = "{\"id\":\"a\",\"message\":\"luz 100\",\"amount\":\"100\",\"category\":\"Servicios\"}";

        assertThatThrownBy(() -> ExtractionDataset.load(file(line, line)))
                .hasMessageContaining("line 2")
                .hasMessageContaining("duplicate id");
    }

    @Test
    void rejectsAMissingMessage() throws IOException {
        Path dataset = file("{\"id\":\"a\",\"amount\":\"100\",\"category\":\"Servicios\"}");

        assertThatThrownBy(() -> ExtractionDataset.load(dataset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 1");
    }
}
