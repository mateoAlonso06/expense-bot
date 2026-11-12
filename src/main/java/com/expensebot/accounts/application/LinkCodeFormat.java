package com.expensebot.accounts.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * How a link code looks and how it is stored. The code is typed by hand on a phone, so the alphabet leaves out
 * look-alike characters (0/O, 1/I/L). It is random with high entropy, so a plain SHA-256 is enough to store it:
 * a slow or salted hash only matters for low-entropy secrets such as passwords.
 */
final class LinkCodeFormat {
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;

    private LinkCodeFormat() {
    }

    /** Eight random characters shown as {@code XXXX-XXXX}. */
    static String generate(SecureRandom random) {
        var code = new StringBuilder();
        for (int i = 0; i < LENGTH; i++) {
            if (i == LENGTH / 2) {
                code.append('-');
            }
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** What the user typed, reduced to the canonical form that gets hashed: no spaces or dash, upper case. */
    static String normalize(String input) {
        return input.strip().replace("-", "").toUpperCase(Locale.ROOT);
    }

    /** Whether the text, once normalized, has the shape of a code. Says nothing about whether it is a real one. */
    static boolean looksLikeCode(String input) {
        String code = normalize(input);
        if (code.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    static String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
