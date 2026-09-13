package com.afinco.backend.repository;

import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.repository.projection.CategoryAggregation;
import com.afinco.backend.repository.projection.DailyCategorySpending;
import com.afinco.backend.repository.projection.DailyTransactionCount;
import com.afinco.backend.repository.projection.StatementCategorySpending;
import com.afinco.backend.repository.projection.StatementTransactionCount;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    boolean existsByHashSignature(String hashSignature);

    @Query("SELECT DISTINCT tx.hashSignature FROM Transaction tx WHERE tx.hashSignature IN :hashSignatures")
    Set<String> findExistingHashSignatures(@Param("hashSignatures") Collection<String> hashSignatures);

    @Query(
            value = """
                    SELECT tx
                    FROM Transaction tx
                    JOIN FETCH tx.account account
                    JOIN FETCH tx.category category
                    LEFT JOIN FETCH tx.statement statement
                    WHERE (:startDate IS NULL OR tx.date >= :startDate)
                      AND (:endDate IS NULL OR tx.date <= :endDate)
                      AND (:statementId IS NULL OR statement.id = :statementId)
                      AND (:accountId IS NULL OR account.id = :accountId)
                      AND (:bankName IS NULL OR account.bankName = :bankName)
                      AND (:categoryId IS NULL OR category.id = :categoryId)
                      AND (:type IS NULL OR tx.type = :type)
                      AND (:status IS NULL OR tx.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(tx)
                    FROM Transaction tx
                    LEFT JOIN tx.statement statement
                    WHERE (:startDate IS NULL OR tx.date >= :startDate)
                      AND (:endDate IS NULL OR tx.date <= :endDate)
                      AND (:statementId IS NULL OR statement.id = :statementId)
                      AND (:accountId IS NULL OR tx.account.id = :accountId)
                      AND (:bankName IS NULL OR tx.account.bankName = :bankName)
                      AND (:categoryId IS NULL OR tx.category.id = :categoryId)
                      AND (:type IS NULL OR tx.type = :type)
                      AND (:status IS NULL OR tx.status = :status)
                    """)
    Page<Transaction> findAllMatchingFilters(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statementId") Long statementId,
            @Param("accountId") Long accountId,
            @Param("bankName") String bankName,
            @Param("categoryId") Long categoryId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            Pageable pageable);

    long countByStatementId(Long statementId);

    @Query("""
            SELECT statement.id AS statementId, COUNT(tx) AS transactionCount
            FROM Transaction tx
            JOIN tx.statement statement
            GROUP BY statement.id
            """)
    List<StatementTransactionCount> countByStatement();

    /**
     * Per-day counts stay small (at most one row per calendar day) and are grouped into months in Java.
     * A null bank name counts every bank.
     */
    @Query("""
            SELECT tx.date AS date, COUNT(tx) AS transactionCount
            FROM Transaction tx
            WHERE (:bankName IS NULL OR tx.account.bankName = :bankName)
            GROUP BY tx.date
            """)
    List<DailyTransactionCount> countByDate(@Param("bankName") String bankName);

    @Query("""
            SELECT category.id AS categoryId,
                   category.name AS categoryName,
                   category.colorCode AS colorCode,
                   SUM(tx.amount) AS totalAmount,
                   COUNT(tx) AS transactionCount
            FROM Transaction tx
            JOIN tx.category category
            WHERE (:startDate IS NULL OR tx.date >= :startDate)
              AND (:endDate IS NULL OR tx.date <= :endDate)
              AND (:accountId IS NULL OR tx.account.id = :accountId)
              AND (:type IS NULL OR tx.type = :type)
              AND tx.status = com.afinco.backend.domain.TransactionStatus.CONFIRMED
            GROUP BY category.id, category.name, category.colorCode
            ORDER BY SUM(tx.amount) DESC
            """)
    List<CategoryAggregation> aggregateConfirmedByCategory(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("accountId") Long accountId,
            @Param("type") TransactionType type);

    /**
     * Confirmed non-payment spending per day and category, grouped into months in Java (SQLite has no
     * portable JPQL month function). A null bank name includes every bank.
     */
    @Query("""
            SELECT tx.date AS date,
                   category.id AS categoryId,
                   SUM(tx.amount) AS totalAmount,
                   COUNT(tx) AS transactionCount
            FROM Transaction tx
            JOIN tx.category category
            JOIN tx.account account
            WHERE tx.status = com.afinco.backend.domain.TransactionStatus.CONFIRMED
              AND category.expenseType <> com.afinco.backend.domain.ExpenseType.PAYMENT
              AND (:bankName IS NULL OR account.bankName = :bankName)
            GROUP BY tx.date, category.id
            """)
    List<DailyCategorySpending> sumSpendingByDateAndCategory(@Param("bankName") String bankName);

    /** Confirmed non-payment spending per statement and category. Manual entries have no statement. */
    @Query("""
            SELECT statement.id AS statementId,
                   category.id AS categoryId,
                   SUM(tx.amount) AS totalAmount,
                   COUNT(tx) AS transactionCount
            FROM Transaction tx
            JOIN tx.statement statement
            JOIN tx.category category
            JOIN tx.account account
            WHERE tx.status = com.afinco.backend.domain.TransactionStatus.CONFIRMED
              AND category.expenseType <> com.afinco.backend.domain.ExpenseType.PAYMENT
              AND (:bankName IS NULL OR account.bankName = :bankName)
            GROUP BY statement.id, category.id
            """)
    List<StatementCategorySpending> sumSpendingByStatementAndCategory(@Param("bankName") String bankName);
}
