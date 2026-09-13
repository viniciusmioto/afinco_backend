package com.afinco.backend.repository;

import com.afinco.backend.domain.Statement;
import com.afinco.backend.domain.StatementType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatementRepository extends JpaRepository<Statement, Long> {

    @Query("""
            SELECT statement
            FROM Statement statement
            WHERE statement.account.id = :accountId
              AND statement.statementType = :statementType
              AND statement.period.startDate = :periodStart
              AND statement.period.endDate = :periodEnd
            """)
    Optional<Statement> findByNaturalKey(
            @Param("accountId") Long accountId,
            @Param("statementType") StatementType statementType,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    @Query("""
            SELECT statement
            FROM Statement statement
            JOIN FETCH statement.account
            ORDER BY statement.period.endDate DESC, statement.id DESC
            """)
    List<Statement> findAllNewestFirst();

    /** Statements of one bank (every bank when null), oldest billing period first. */
    @Query("""
            SELECT statement
            FROM Statement statement
            JOIN FETCH statement.account account
            WHERE (:bankName IS NULL OR account.bankName = :bankName)
            ORDER BY statement.period.endDate ASC, statement.id ASC
            """)
    List<Statement> findAllForBankOldestFirst(@Param("bankName") String bankName);
}
