-- V1__initial_schema.sql
-- AlphaHarvester Global Market Intelligence Database Schema (H2 PostgreSQL Mode)

CREATE TABLE IF NOT EXISTS global_asset_metadata (
    id UUID PRIMARY KEY,
    ticker VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    listing_date TIMESTAMP NOT NULL,
    underlying_index VARCHAR(255),
    total_expense_ratio DECIMAL(6,4),
    fund_size_twd DECIMAL(18,2),
    asset_class VARCHAR(32) NOT NULL,
    distribution_frequency VARCHAR(32),
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS benchmark_index (
    id UUID PRIMARY KEY,
    ticker VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    region VARCHAR(64),
    description TEXT,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS market_daily_quote (
    id UUID PRIMARY KEY,
    asset_id UUID REFERENCES global_asset_metadata(id) ON DELETE CASCADE,
    benchmark_id UUID REFERENCES benchmark_index(id) ON DELETE CASCADE,
    ticker VARCHAR(32) NOT NULL,
    trade_date TIMESTAMP NOT NULL,
    open_price DECIMAL(12,4),
    high_price DECIMAL(12,4),
    low_price DECIMAL(12,4),
    close_price DECIMAL(12,4) NOT NULL,
    volume_shares BIGINT,
    trade_value_twd DECIMAL(18,2),
    net_asset_value DECIMAL(12,4),
    discount_premium_percentage DECIMAL(6,4),
    CONSTRAINT uq_market_daily_quote UNIQUE (ticker, trade_date)
);

CREATE TABLE IF NOT EXISTS macro_yield_snapshot (
    id UUID PRIMARY KEY,
    record_date TIMESTAMP NOT NULL UNIQUE,
    us_corporate_bond_effective_yield DECIMAL(6,4) NOT NULL,
    us_10_year_treasury_yield DECIMAL(6,4) NOT NULL,
    us_20_year_treasury_yield DECIMAL(6,4) NOT NULL,
    yield_spread_10y_minus_2y DECIMAL(6,4) NOT NULL
);

CREATE TABLE IF NOT EXISTS global_asset_score (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES global_asset_metadata(id) ON DELETE CASCADE,
    ticker VARCHAR(32) NOT NULL,
    evaluation_date TIMESTAMP NOT NULL,
    asset_class VARCHAR(32) NOT NULL,
    class_rank INT NOT NULL,
    composite_score DECIMAL(6,2) NOT NULL,
    total_expense_ratio DECIMAL(6,4),
    fund_size_twd DECIMAL(18,2),
    is_qualified BOOLEAN NOT NULL DEFAULT TRUE,
    disqualification_reason TEXT,
    CONSTRAINT uq_global_asset_score UNIQUE (ticker, evaluation_date)
);

CREATE TABLE IF NOT EXISTS dca_popularity_rank (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES global_asset_metadata(id) ON DELETE CASCADE,
    ticker VARCHAR(32) NOT NULL,
    ranking_year INT NOT NULL,
    ranking_month INT NOT NULL,
    rank_position INT NOT NULL,
    regular_investor_count INT NOT NULL,
    CONSTRAINT uq_dca_popularity_rank UNIQUE (ticker, ranking_year, ranking_month)
);

CREATE TABLE IF NOT EXISTS dividend_announcement (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES global_asset_metadata(id) ON DELETE CASCADE,
    ticker VARCHAR(32) NOT NULL,
    ex_date TIMESTAMP NOT NULL,
    payment_date TIMESTAMP NOT NULL,
    dividend_per_share DECIMAL(10,4) NOT NULL,
    tax_tag VARCHAR(32) NOT NULL,
    CONSTRAINT uq_dividend_announcement UNIQUE (ticker, ex_date)
);

CREATE TABLE IF NOT EXISTS corporate_action (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES global_asset_metadata(id) ON DELETE CASCADE,
    ticker VARCHAR(32) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    effective_date TIMESTAMP NOT NULL,
    split_to_shares INT NOT NULL,
    split_from_shares INT NOT NULL,
    CONSTRAINT uq_corporate_action UNIQUE (ticker, effective_date)
);

CREATE TABLE IF NOT EXISTS data_feed_sync_watermark (
    id UUID PRIMARY KEY,
    feed_name VARCHAR(64) NOT NULL UNIQUE,
    last_successful_sync_at TIMESTAMP NOT NULL,
    latest_record_date TIMESTAMP NOT NULL,
    records_synced_count INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quote_trade_date ON market_daily_quote(trade_date);
CREATE INDEX IF NOT EXISTS idx_quote_ticker ON market_daily_quote(ticker);
CREATE INDEX IF NOT EXISTS idx_score_eval_class ON global_asset_score(asset_class, evaluation_date, class_rank);
CREATE INDEX IF NOT EXISTS idx_dca_rank ON dca_popularity_rank(ranking_year, ranking_month, rank_position);
CREATE INDEX IF NOT EXISTS idx_dividend_payment ON dividend_announcement(payment_date);
CREATE INDEX IF NOT EXISTS idx_watermark_feed_name ON data_feed_sync_watermark(feed_name);


