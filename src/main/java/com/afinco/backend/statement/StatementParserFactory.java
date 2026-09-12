package com.afinco.backend.statement;

import com.afinco.backend.domain.StatementType;
import com.afinco.backend.exception.UnsupportedStatementException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StatementParserFactory {
    private final List<StatementParser> parsers;

    public StatementParserFactory(List<StatementParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public StatementParser getParser(StatementType statementType, String documentText) {
        List<StatementParser> matching = parsers.stream()
                .filter(parser -> parser.statementType() == statementType)
                .filter(parser -> parser.supports(documentText)).toList();
        if (matching.size() != 1) {
            throw new UnsupportedStatementException("Only recognized TD credit-card statements are supported");
        }
        return matching.getFirst();
    }
}
