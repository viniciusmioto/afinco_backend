-- =============================================================================
-- V2: Categorization model
--
-- Adds expense_type to categories and replaces the seed data with the
-- four-type system: PAYMENT, FIXED, VARIABLE, OCCASIONAL.
-- =============================================================================

-- 1. Add the expense_type column with a safe default for any leftover rows.
ALTER TABLE categories ADD COLUMN expense_type VARCHAR(20) NOT NULL DEFAULT 'OCCASIONAL'
    CHECK (expense_type IN ('PAYMENT', 'FIXED', 'VARIABLE', 'OCCASIONAL'));

-- 2. Remove the placeholder seed data from V1.
DELETE FROM categories;

-- 3. Insert the definitive category list.
INSERT INTO categories (name, expense_type, color_code) VALUES
    -- PAYMENT (credit card payments, rewards)
    ('Payment',            'PAYMENT',    '#10B981'),

    -- FIXED (predictable monthly charges)
    ('Subscriptions',      'FIXED',      '#8B5CF6'),
    ('Phone / Internet',   'FIXED',      '#6366F1'),
    ('Transport',          'FIXED',      '#0EA5E9'),
    ('Rent',               'FIXED',      '#4F46E5'),

    -- VARIABLE (monthly necessities, fluctuating amounts)
    ('Groceries',          'VARIABLE',   '#2563EB'),
    ('Food & Leisure',     'VARIABLE',   '#F59E0B'),
    ('Pharmacy & Health',  'VARIABLE',   '#EF4444'),
    ('Electricity & Water','VARIABLE',   '#64748B'),

    -- OCCASIONAL (sporadic / lifestyle / fallback)
    ('Occasional',         'OCCASIONAL', '#EC4899');
