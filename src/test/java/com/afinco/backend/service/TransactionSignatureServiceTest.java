package com.afinco.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TransactionSignatureServiceTest {

    private final TransactionSignatureService service = new TransactionSignatureService();

    @Test
    void producesKnownSha256SignatureFromLengthPrefixedCanonicalFields() {
        String result = service.calculate(
                LocalDate.of(2026, 9, 11), new BigDecimal("42.35"), "Corner Market", "TD Bank");

        assertThat(result).isEqualTo("4f716e49df05ac9de5809d014913e3f494f46f922c74e77bdc42a4c29ab86507");
    }

    @Test
    void canonicalizesAmountScaleWithoutLosingPrecision() {
        String expected = signature(new BigDecimal("42"), "Market", "TD Bank");

        assertThat(signature(new BigDecimal("42.00"), "Market", "TD Bank")).isEqualTo(expected);
        assertThat(signature(new BigDecimal("42.0000"), "Market", "TD Bank")).isEqualTo(expected);
        assertThat(signature(new BigDecimal("4.2E+1"), "Market", "TD Bank")).isEqualTo(expected);
    }

    @Test
    void normalizesUnicodeWhitespaceCompatibilityCharactersAndCase() {
        String expected = signature(new BigDecimal("42.35"), "Café & Market #1", "TD Bank");

        String actual = signature(
                new BigDecimal("42.35"), "\u2003ＣＡＦＥ\u0301\u00a0&\tMarket\n#1\u2003", "  td\u202fBANK  ");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void keepsPunctuationAndInternalDescriptionContentSignificant() {
        String expected = signature(new BigDecimal("42.35"), "O'Brien & Sons #42", "TD Bank");

        assertThat(signature(new BigDecimal("42.35"), "OBrien & Sons #42", "TD Bank"))
                .isNotEqualTo(expected);
        assertThat(signature(new BigDecimal("42.35"), "O'Brien & Sons #43", "TD Bank"))
                .isNotEqualTo(expected);
    }

    @Test
    void separatesFieldsUnambiguously() {
        String first = signature(new BigDecimal("1.00"), "ab", "c");
        String second = signature(new BigDecimal("1.00"), "a", "bc");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void recognizesCommonTdBankNames() {
        String expected = signature(new BigDecimal("42.35"), "Market", "TD Bank");

        assertThat(signature(new BigDecimal("42.35"), "Market", "TD")).isEqualTo(expected);
        assertThat(signature(new BigDecimal("42.35"), "Market", "TD Canada Trust")).isEqualTo(expected);
    }

    @Test
    void includesEveryRequestedFieldInTheSignature() {
        LocalDate date = LocalDate.of(2026, 9, 11);
        String expected = service.calculate(date, new BigDecimal("42.35"), "Market", "TD Bank");

        assertThat(service.calculate(date.plusDays(1), new BigDecimal("42.35"), "Market", "TD Bank"))
                .isNotEqualTo(expected);
        assertThat(service.calculate(date, new BigDecimal("42.36"), "Market", "TD Bank"))
                .isNotEqualTo(expected);
        assertThat(service.calculate(date, new BigDecimal("42.35"), "Market Annex", "TD Bank"))
                .isNotEqualTo(expected);
        assertThat(service.calculate(date, new BigDecimal("42.35"), "Market", "Another Bank"))
                .isNotEqualTo(expected);
    }

    @Test
    void normalizesCaseIndependentlyOfTheSystemLocale() {
        Locale previous = Locale.getDefault();
        String expected = signature(new BigDecimal("1.00"), "INCOME", "TD BANK");
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(signature(new BigDecimal("1.00"), "INCOME", "TD BANK")).isEqualTo(expected);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsMissingOrBlankFields() {
        LocalDate date = LocalDate.of(2026, 9, 11);
        BigDecimal amount = new BigDecimal("42.35");

        assertThatThrownBy(() -> service.calculate(null, amount, "Market", "TD Bank"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(date, null, "Market", "TD Bank"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(date, amount, null, "TD Bank"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(date, amount, "\u2003\t\n", "TD Bank"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(date, amount, "Market", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(date, amount, "Market", " \u00a0 "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeAmountsAndAmountsThatWouldRequireRounding() {
        assertThatThrownBy(() -> signature(new BigDecimal("-0.01"), "Market", "TD Bank"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> signature(new BigDecimal("42.351"), "Market", "TD Bank"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String signature(BigDecimal amount, String description, String bankName) {
        return service.calculate(LocalDate.of(2026, 9, 11), amount, description, bankName);
    }
}
