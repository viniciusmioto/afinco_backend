package com.afinco.backend.service;

import com.afinco.backend.domain.Statement;
import com.afinco.backend.domain.StatementPeriod;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The days each account's imported statements cover. A calendar month is only fully known when every
 * account with statements around that month has statements for all of its days.
 */
final class StatementCoverage {

    private record DateRange(LocalDate start, LocalDate end) {

        boolean overlaps(LocalDate first, LocalDate last) {
            return !start.isAfter(last) && !end.isBefore(first);
        }

        boolean contains(LocalDate first, LocalDate last) {
            return !start.isAfter(first) && !end.isBefore(last);
        }
    }

    private final List<List<DateRange>> rangesByAccount;

    private StatementCoverage(List<List<DateRange>> rangesByAccount) {
        this.rangesByAccount = rangesByAccount;
    }

    static StatementCoverage of(List<Statement> statements) {
        Map<Long, List<StatementPeriod>> periodsByAccount = new LinkedHashMap<>();
        for (Statement statement : statements) {
            periodsByAccount.computeIfAbsent(statement.getAccount().getId(), id -> new ArrayList<>())
                    .add(statement.getPeriod());
        }
        return new StatementCoverage(periodsByAccount.values().stream().map(StatementCoverage::merge).toList());
    }

    /** Joins overlapping and back-to-back periods (Feb 14 – Mar 13 and Mar 14 – Apr 13) into one range. */
    private static List<DateRange> merge(List<StatementPeriod> periods) {
        List<DateRange> merged = new ArrayList<>();
        periods.stream()
                .sorted(Comparator.comparing(StatementPeriod::startDate))
                .forEach(period -> {
                    DateRange last = merged.isEmpty() ? null : merged.getLast();
                    if (last != null && !period.startDate().isAfter(last.end().plusDays(1))) {
                        LocalDate end = period.endDate().isAfter(last.end()) ? period.endDate() : last.end();
                        merged.set(merged.size() - 1, new DateRange(last.start(), end));
                    } else {
                        merged.add(new DateRange(period.startDate(), period.endDate()));
                    }
                });
        return merged;
    }

    Optional<LocalDate> firstDay() {
        return rangesByAccount.stream().flatMap(List::stream).map(DateRange::start).min(Comparator.naturalOrder());
    }

    Optional<LocalDate> lastDay() {
        return rangesByAccount.stream().flatMap(List::stream).map(DateRange::end).max(Comparator.naturalOrder());
    }

    /**
     * Whether the month is fully covered. Months that no statement touches (manual entries only) count as
     * complete once they are over.
     */
    boolean isComplete(YearMonth month, LocalDate today) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        boolean touched = false;
        for (List<DateRange> ranges : rangesByAccount) {
            if (ranges.stream().noneMatch(range -> range.overlaps(first, last))) {
                continue;
            }
            if (ranges.stream().noneMatch(range -> range.contains(first, last))) {
                return false;
            }
            touched = true;
        }
        return touched || last.isBefore(today);
    }
}
