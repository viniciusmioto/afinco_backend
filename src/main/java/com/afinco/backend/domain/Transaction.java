package com.afinco.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** The imported statement this row came from; null for manual entries. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "statement_id")
    private Statement statement;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private TransactionType type;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "hash_signature", nullable = false, length = 64)
    private String hashSignature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Lob
    @Column(name = "raw_text")
    private String rawText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Creates a manual transaction that does not belong to any statement. */
    public Transaction(
            Account account,
            Category category,
            LocalDate date,
            BigDecimal amount,
            TransactionType type,
            String description,
            String hashSignature,
            TransactionStatus status,
            String rawText) {
        this(null, account, category, date, amount, type, description, hashSignature, status, rawText);
    }

    public Transaction(
            Statement statement,
            Account account,
            Category category,
            LocalDate date,
            BigDecimal amount,
            TransactionType type,
            String description,
            String hashSignature,
            TransactionStatus status,
            String rawText) {
        this.account = Objects.requireNonNull(account, "Account must not be null");
        if (statement != null && !statement.belongsTo(account)) {
            throw new IllegalArgumentException("A transaction must use its statement's account");
        }
        this.statement = statement;
        this.category = Objects.requireNonNull(category, "Category must not be null");
        this.date = Objects.requireNonNull(date, "Transaction date must not be null");
        this.amount = requirePositiveAmount(amount);
        this.type = Objects.requireNonNull(type, "Transaction type must not be null");
        this.description = requireText(description, "Description");
        this.hashSignature = requireSha256(hashSignature);
        this.status = Objects.requireNonNull(status, "Transaction status must not be null");
        this.rawText = rawText;
    }

    @PrePersist
    void initializeCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    public void confirmDuplicate() {
        if (status != TransactionStatus.DUPLICATE_PENDING) {
            throw new IllegalStateException("Only pending duplicates can be confirmed");
        }
        status = TransactionStatus.CONFIRMED;
    }

    private static BigDecimal requirePositiveAmount(BigDecimal value) {
        Objects.requireNonNull(value, "Amount must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String requireSha256(String value) {
        if (value == null || !value.matches("[0-9A-Fa-f]{64}")) {
            throw new IllegalArgumentException("Hash signature must be a 64-character SHA-256 hex value");
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
