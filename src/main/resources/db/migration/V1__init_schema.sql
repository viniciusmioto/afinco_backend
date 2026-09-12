CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bank_name VARCHAR(100) NOT NULL,
    account_number_last4 VARCHAR(4) NOT NULL
        CHECK (length(account_number_last4) = 4 AND account_number_last4 NOT GLOB '*[^0-9]*'),
    currency VARCHAR(3) NOT NULL
        CHECK (length(currency) = 3 AND currency = upper(currency)),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    color_code VARCHAR(7) NOT NULL
        CHECK (length(color_code) = 7 AND substr(color_code, 1, 1) = '#')
);

CREATE TABLE transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    category_id INTEGER NOT NULL,
    date DATE NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    type VARCHAR(6) NOT NULL CHECK (type IN ('CREDIT', 'DEBIT')),
    description VARCHAR(500) NOT NULL,
    hash_signature VARCHAR(64) NOT NULL CHECK (length(hash_signature) = 64),
    status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'DUPLICATE_PENDING')),
    raw_text TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_category
        FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT
);

CREATE INDEX idx_transactions_account_id ON transactions (account_id);
CREATE INDEX idx_transactions_category_id ON transactions (category_id);
CREATE INDEX idx_transactions_date ON transactions (date);
CREATE INDEX idx_transactions_hash_signature ON transactions (hash_signature);
CREATE INDEX idx_transactions_status ON transactions (status);

INSERT INTO categories (name, color_code) VALUES
    ('Groceries', '#2563EB'),
    ('Transportation', '#0D9488'),
    ('Housing & Rent', '#4F46E5'),
    ('Utilities', '#64748B'),
    ('Leisure & Entertainment', '#7C3AED'),
    ('Education', '#0891B2'),
    ('Income', '#16A34A'),
    ('Uncategorized', '#6B7280');
