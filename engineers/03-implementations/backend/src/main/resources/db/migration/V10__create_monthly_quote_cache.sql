-- V10__create_monthly_quote_cache.sql
-- Ehcache 3 Persistent Backing Store for Monthly Indicator Quote Chunks

CREATE TABLE IF NOT EXISTS monthly_quote_cache (
    cache_key VARCHAR(64) PRIMARY KEY,
    payload_data BYTEA NOT NULL
);

