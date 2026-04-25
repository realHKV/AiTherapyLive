-- V2__add_onboarding_fields.sql
-- Adds onboarding fields to therapy_profiles for user flow

ALTER TABLE therapy_profiles
    ADD COLUMN IF NOT EXISTS gender               VARCHAR(30),
    ADD COLUMN IF NOT EXISTS country              VARCHAR(100),
    ADD COLUMN IF NOT EXISTS enjoyments           TEXT,              -- ENCRYPTED — what user likes/enjoys
    ADD COLUMN IF NOT EXISTS selected_therapist   VARCHAR(50),       -- 'dr_sarah' | 'dr_alex' etc.
    ADD COLUMN IF NOT EXISTS onboarding_complete  BOOLEAN NOT NULL DEFAULT false;
