-- Rollback: ALTER TABLE assignments DROP COLUMN note;
ALTER TABLE assignments ADD COLUMN note TEXT;
