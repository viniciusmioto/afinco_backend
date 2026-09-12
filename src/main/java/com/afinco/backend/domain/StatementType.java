package com.afinco.backend.domain;

/** Kind of account statement; it also decides the transaction type every row of that statement gets. */
public enum StatementType {
    CREDIT_CARD(TransactionType.CREDIT),
    CHECKING_ACCOUNT(TransactionType.DEBIT);

    private final TransactionType transactionType;

    StatementType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public TransactionType transactionType() {
        return transactionType;
    }
}
