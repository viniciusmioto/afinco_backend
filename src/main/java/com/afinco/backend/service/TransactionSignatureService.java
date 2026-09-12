package com.afinco.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Produces stable transaction signatures from date, amount, description and bank name.
 * Each canonical field is encoded as UTF-8 and prefixed by its four-byte, big-endian
 * byte length, so field boundaries cannot collide. Dates use ISO-8601, amounts use
 * exactly two decimal places, and text uses NFKC, collapsed Unicode whitespace and
 * locale-independent lower case. Transaction type is deliberately not a hash field.
 */
@Component
public class TransactionSignatureService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    public String calculate(LocalDate date, BigDecimal amount, String description, String bankName) {
        if (date == null) {
            throw new IllegalArgumentException("Transaction date is required");
        }

        String canonicalAmount = normalizeAmount(amount);
        String canonicalDescription = normalizeText(description, "Transaction description");
        String canonicalBankName = normalizeText(bankName, "Bank name");
        if (canonicalBankName.equals("td") || canonicalBankName.equals("td canada trust")) {
            canonicalBankName = "td bank";
        }
        MessageDigest digest = createDigest();
        addField(digest, date.toString());
        addField(digest, canonicalAmount);
        addField(digest, canonicalDescription);
        addField(digest, canonicalBankName);
        return HexFormat.of().formatHex(digest.digest());
    }

    private String normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Transaction amount must be non-negative");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Transaction amount must have at most two decimal places", exception);
        }
    }

    private String normalizeText(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = WHITESPACE.matcher(Normalizer.normalize(value, Normalizer.Form.NFKC))
                .replaceAll(" ")
                .strip()
                .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The Java runtime does not support SHA-256", exception);
        }
    }

    private void addField(MessageDigest digest, String field) {
        byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
