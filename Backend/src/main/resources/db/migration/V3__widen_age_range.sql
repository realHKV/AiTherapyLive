-- V3__widen_age_range.sql
-- Widens age_range from VARCHAR(20) to VARCHAR(50) to prevent overflow
-- when the AI summarizer writes back a free-form age string.

ALTER TABLE therapy_profiles
    ALTER COLUMN age_range TYPE VARCHAR(50);
