-- =============================================================================
-- V4: Imported statements
--
-- A statement groups the transactions imported from one bank document. The
-- natural key (account, type, period) lets a re-import append to the existing
-- statement instead of creating a second copy of the same billing period.
-- Existing and manual transactions keep a NULL statement_id.
-- =============================================================================

CREATE TABLE statements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    statement_type VARCHAR(20) NOT NULL
        CHECK (statement_type IN ('CREDIT_CARD', 'CHECKING_ACCOUNT')),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_statements_period CHECK (period_start <= period_end),
    CONSTRAINT uq_statements_account_type_period
        UNIQUE (account_id, statement_type, period_start, period_end),
    CONSTRAINT fk_statements_account
        FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT
);

CREATE INDEX idx_statements_period_end ON statements (period_end);

-- SQLite can add a foreign-key column in place when its default is NULL.
ALTER TABLE transactions ADD COLUMN statement_id INTEGER
    REFERENCES statements (id) ON DELETE RESTRICT;

CREATE INDEX idx_transactions_statement_id ON transactions (statement_id);
