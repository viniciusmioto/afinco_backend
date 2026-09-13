-- =============================================================================
-- V5: Rename "Phone / Internet" to "Phone & Internet"
--
-- Renamed in place so existing transactions keep their category id. The guard
-- leaves the row untouched if a "Phone & Internet" category was added manually.
-- =============================================================================

UPDATE categories
SET name = 'Phone & Internet'
WHERE name = 'Phone / Internet'
  AND NOT EXISTS (SELECT 1 FROM categories target WHERE target.name = 'Phone & Internet');
