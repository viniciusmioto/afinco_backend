package com.afinco.backend.statement;

import com.afinco.backend.domain.StatementType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.afinco.backend.exception.UnsupportedStatementException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatementParserFactoryTest {

    private final StatementParser parser = new TDBankStatementParser(new PdfTextExtractor());
    private final StatementParserFactory factory = new StatementParserFactory(List.of(parser));

    @ParameterizedTest
    @ValueSource(strings = {"TD CASH BACK VISA", "TD VISA", "TD CANADA TRUST VISA"})
    void selectsTdParserFromCreditCardStatementSignature(String bankSignature) {
        String documentText = bankSignature + "\nSTATEMENT DATE: January 15, 2026\n"
                + "TRANSACTION POSTING\nDATE DATE ACTIVITY DESCRIPTION AMOUNT($)\n";

        assertThat(factory.getParser(StatementType.CREDIT_CARD, documentText)).isSameAs(parser);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "RBC ROYAL BANK VISA\nSTATEMENT DATE: January 15, 2026",
            "OTHER BANK\nSTATEMENT DATE: January 15, 2026",
            "TD CANADA TRUST\nCHEQUING ACCOUNT STATEMENT",
            "A letter mentioning TD VISA with no statement table",
            ""
    })
    void rejectsUnsupportedBanksAndUnrecognizedStatementLayouts(String documentText) {
        assertThatThrownBy(() -> factory.getParser(StatementType.CREDIT_CARD, documentText))
                .isInstanceOf(UnsupportedStatementException.class);
    }

    @Test
    void rejectsCheckingAccountUntilItsParserIsImplemented() {
        String documentText = "TD CANADA TRUST\nCHEQUING ACCOUNT STATEMENT";

        assertThatThrownBy(() -> factory.getParser(StatementType.CHECKING_ACCOUNT, documentText))
                .isInstanceOf(UnsupportedStatementException.class);
    }

    @Test
    void rejectsAmbiguousParserRegistrations() {
        StatementParserFactory ambiguous = new StatementParserFactory(List.of(parser, parser));
        String documentText = "TD VISA\nSTATEMENT DATE: January 15, 2026\n"
                + "TRANSACTION POSTING\nDATE DATE ACTIVITY DESCRIPTION AMOUNT($)\n";

        assertThatThrownBy(() -> ambiguous.getParser(StatementType.CREDIT_CARD, documentText))
                .isInstanceOf(UnsupportedStatementException.class);
    }
}
