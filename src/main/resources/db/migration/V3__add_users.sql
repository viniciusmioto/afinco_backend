CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    email VARCHAR(254) COLLATE NOCASE NOT NULL UNIQUE
        CHECK (length(email) BETWEEN 3 AND 254 AND email = lower(trim(email))),
    password_hash VARCHAR(60) NOT NULL
        CHECK (length(password_hash) = 60),
    enabled INTEGER NOT NULL DEFAULT 1
        CHECK (enabled IN (0, 1)),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Temporary local test user requested for the initial authenticated MVP.
-- The BCrypt cost is 12. Replace this credential before exposing Afinco outside
-- a trusted network; see docs/authentication.md.
INSERT INTO users (email, password_hash)
VALUES (
    'test@test.com',
    '$2a$12$9fgMDPBPa4uXj7rW8WNGIuO.vvEIR2FmRX8yNt1xIzFD535.4MfRa'
);
