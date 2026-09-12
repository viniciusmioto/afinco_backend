-- =============================================================================
-- V2: Categorization model
--
-- Adds expense_type to categories and evolves the original seeds into the
-- four-type system: PAYMENT, FIXED, VARIABLE, OCCASIONAL.
--
-- Category rows are updated in place so an upgrade never breaks transaction
-- foreign keys or silently changes the meaning of an existing category id.
-- =============================================================================

-- 1. Add the expense_type column with a safe default for legacy/custom rows.
ALTER TABLE categories ADD COLUMN expense_type VARCHAR(20) NOT NULL DEFAULT 'OCCASIONAL'
    CHECK (expense_type IN ('PAYMENT', 'FIXED', 'VARIABLE', 'OCCASIONAL'));

-- 2. Rename compatible V1 seeds in place, preserving their ids and references.
-- A target-name guard also keeps this safe if a category was added manually.
UPDATE categories
SET name = 'Transport', expense_type = 'FIXED', color_code = '#0EA5E9'
WHERE name = 'Transportation'
  AND NOT EXISTS (SELECT 1 FROM categories target WHERE target.name = 'Transport');

UPDATE categories
SET name = 'Rent', expense_type = 'FIXED', color_code = '#4F46E5'
WHERE name = 'Housing & Rent'
  AND NOT EXISTS (SELECT 1 FROM categories target WHERE target.name = 'Rent');

UPDATE categories
SET name = 'Electricity & Water', expense_type = 'VARIABLE', color_code = '#64748B'
WHERE name = 'Utilities'
  AND NOT EXISTS (SELECT 1 FROM categories target WHERE target.name = 'Electricity & Water');

UPDATE categories
SET name = 'Food & Leisure', expense_type = 'VARIABLE', color_code = '#F59E0B'
WHERE name = 'Leisure & Entertainment'
  AND NOT EXISTS (SELECT 1 FROM categories target WHERE target.name = 'Food & Leisure');

UPDATE categories
SET name = 'Occasional', expense_type = 'OCCASIONAL', color_code = '#EC4899'
WHERE name = 'Uncategorized'
  AND NOT EXISTS (SELECT 1 FROM categories target WHERE target.name = 'Occasional');

-- Keep any guarded legacy aliases correctly typed if their target already existed.
UPDATE categories SET expense_type = 'VARIABLE' WHERE name = 'Groceries';
UPDATE categories SET expense_type = 'FIXED' WHERE name IN ('Transportation', 'Housing & Rent');
UPDATE categories SET expense_type = 'VARIABLE'
WHERE name IN ('Utilities', 'Leisure & Entertainment');

-- V1's Education and Income placeholders are not part of the new taxonomy.
-- Remove them only when no transaction references them; used rows remain as
-- OCCASIONAL legacy categories so existing financial data is never discarded.
DELETE FROM categories
WHERE name IN ('Education', 'Income')
  AND NOT EXISTS (
      SELECT 1 FROM transactions
      WHERE transactions.category_id = categories.id
  );

-- 3. Add every category missing from the definitive taxonomy.
INSERT OR IGNORE INTO categories (name, expense_type, color_code) VALUES
    ('Payment',             'PAYMENT',    '#10B981'),
    ('Subscriptions',       'FIXED',      '#8B5CF6'),
    ('Phone / Internet',    'FIXED',      '#6366F1'),
    ('Transport',           'FIXED',      '#0EA5E9'),
    ('Rent',                'FIXED',      '#4F46E5'),
    ('Groceries',           'VARIABLE',   '#2563EB'),
    ('Food & Leisure',      'VARIABLE',   '#F59E0B'),
    ('Pharmacy & Health',   'VARIABLE',   '#EF4444'),
    ('Electricity & Water', 'VARIABLE',   '#64748B'),
    ('Occasional',          'OCCASIONAL', '#EC4899');
