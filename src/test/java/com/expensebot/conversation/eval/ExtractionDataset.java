package com.expensebot.conversation.eval;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * The labeled cases of a JSONL file (one JSON object per line), plus a version that changes whenever the file does.
 * Two runs are only comparable when they used the same dataset version.
 *
 * @param version first 8 hex characters of the file's SHA-256
 */
public record ExtractionDataset(List<ExtractionCase> cases, String version) {

    // Strict on purpose: a typo in a field name ("catgory") must fail instead of silently becoming null.
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public ExtractionDataset {
        cases = List.copyOf(cases);
    }

    public static ExtractionDataset load(Path file) throws IOException {
        byte[] content = Files.readAllBytes(file);
        List<String> lines = new String(content, StandardCharsets.UTF_8).lines().toList();

        List<ExtractionCase> cases = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            int lineNumber = i + 1;
            ExtractionCase parsed = parse(line, lineNumber);
            validate(parsed, lineNumber);
            if (!ids.add(parsed.id())) {
                throw invalid(lineNumber, "duplicate id '" + parsed.id() + "'");
            }
            cases.add(parsed);
        }
        return new ExtractionDataset(cases, shortHash(content));
    }

    private static ExtractionCase parse(String line, int lineNumber) {
        try {
            return JSON.readValue(line, ExtractionCase.class);
        } catch (JacksonException | NullPointerException e) {
            throw invalid(lineNumber, e.getMessage());
        }
    }

    private static void validate(ExtractionCase c, int lineNumber) {
        if (c.id().isBlank() || c.message().isBlank()) {
            throw invalid(lineNumber, "id and message must not be blank");
        }
        if (c.amount() != null && !c.amountCandidates().isEmpty()) {
            throw invalid(lineNumber, "amount and amountCandidates exclude each other");
        }
        if (c.amount() != null && c.amount().signum() <= 0) {
            throw invalid(lineNumber, "amount must be positive");
        }
        if (!c.amountCandidates().isEmpty()) {
            boolean positive = c.amountCandidates().stream().allMatch(a -> a != null && a.signum() > 0);
            long distinct = c.amountCandidates().stream().map(BigDecimal::stripTrailingZeros).distinct().count();
            if (!positive || distinct < 2 || distinct != c.amountCandidates().size()) {
                throw invalid(lineNumber, "amountCandidates must be two or more distinct positive amounts");
            }
        }
        if (!c.isCorrection() && c.reference() != null) {
            throw invalid(lineNumber, "only a correction has a reference");
        }
        switch (c.kind()) {
            case FOUND, AMBIGUOUS -> requireOfferedCategory(c, lineNumber);
            case ASK_AMOUNT -> {
                if (c.category() != null) {
                    requireOfferedCategory(c, lineNumber);
                }
            }
            case CORRECTION -> {
                if (!c.amountCandidates().isEmpty()) {
                    throw invalid(lineNumber, "a correction has no amountCandidates");
                }
                if (c.category() != null) {
                    requireOfferedCategory(c, lineNumber);
                }
            }
            case NOT_AN_EXPENSE -> {
                if (c.category() != null) {
                    throw invalid(lineNumber, "a message that is not an expense cannot expect a category");
                }
            }
        }
    }

    private static void requireOfferedCategory(ExtractionCase c, int lineNumber) {
        if (c.category() == null || !c.offeredCategories().contains(c.category())) {
            throw invalid(lineNumber, "category must be one of " + c.offeredCategories());
        }
    }

    private static IllegalArgumentException invalid(int lineNumber, String reason) {
        return new IllegalArgumentException("Invalid dataset line " + lineNumber + ": " + reason);
    }

    /** First 8 hex characters of the SHA-256 of {@code content}: short enough for names, long enough to differ. */
    static String shortHash(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
