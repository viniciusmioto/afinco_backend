package com.afinco.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One imported bank statement. Its transactions reference it through {@code Transaction.statement};
 * the relationship is intentionally owned by the transaction side so a statement never loads all of
 * its rows implicitly.
 */
@Getter
@Entity
@Table(name = "statements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Statement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "statement_type", nullable = false, length = 20)
    private StatementType statementType;

    @Embedded
    private StatementPeriod period;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Statement(Account account, StatementType statementType, StatementPeriod period) {
        this.account = Objects.requireNonNull(account, "Account must not be null");
        this.statementType = Objects.requireNonNull(statementType, "Statement type must not be null");
        this.period = Objects.requireNonNull(period, "Statement period must not be null");
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    boolean belongsTo(Account candidate) {
        return Objects.equals(account.getId(), candidate.getId());
    }
}
