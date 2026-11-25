-- V11__normalize_estimated_payment_date_28d.sql
-- Normalize Yahoo-estimated payment dates from ex_date + 30 days to ex_date + 28 days (exact 4 weeks)
-- so estimated payment dates always preserve the ex-date's weekday and never fall on weekends.

UPDATE dividend_announcement
SET payment_date = ex_date + INTERVAL '28' DAY;
