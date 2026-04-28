-- =============================================
-- Migration: Alter avatar, cover_photo, bio columns to TEXT
-- Reason: S3 URLs exceed varchar(255) limit
-- Target: Supabase PostgreSQL — table 'users'
-- =============================================

ALTER TABLE users ALTER COLUMN avatar TYPE TEXT;
ALTER TABLE users ALTER COLUMN cover_photo TYPE TEXT;
ALTER TABLE users ALTER COLUMN bio TYPE TEXT;
