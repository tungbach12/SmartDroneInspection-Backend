-- V1 - baseline: extensions only. Per-feature tables land in V2+ migrations.
-- Owned by team leader only (matches EF migration discipline).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS vector;
