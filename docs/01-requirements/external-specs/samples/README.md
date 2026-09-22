# AlphaHarvester External Specs Sample Code & Probe Client

> **Location**: `docs/01-requirements/external-specs/samples/`  
> **Script**: [`verify_external_feeds.py`](verify_external_feeds.py)  
> **Dependencies**: Python 3.8+ (Zero third-party dependencies, standard library only)

---

## 1. Purpose & Capabilities

This reference client implements end-to-end data ingestion, normalization, and mathematical regression across all upstream data providers specified in AlphaHarvester:

1. **TWSE Master ETF Universe** (`/opendata/t187ap47_L`):
   - Ingests 270+ active funds.
   - Converts Republic of China (ROC) dates (`1150922`, `0920630`) to ISO 8601 (`2026-09-22`, `2003-06-30`).
   - Extracts tracked benchmark index and shares outstanding.
2. **TWSE Concentrated Market Daily Quotes** (`/exchangeReport/STOCK_DAY_ALL`):
   - Ingests 1,300+ daily stock and ETF quotes (Close, Volume, Turnover, Transactions).
3. **TPEx OTC Market Daily Quotes** (`/openapi/v1/tpex_mainboard_quotes`):
   - Ingests OTC Bond ETFs (`00679B`, `00720B`, etc.) and OTC Equity ETFs.
4. **TWSE MIS Real-Time Net Asset Value & Discount/Premium** (`all_etf.txt`):
   - Ingests 350+ ETFs with live NAV, market closing price, discount/premium %, and calculates AUM.
5. **TWSE Regular Quota (定期定額) Top 20 Rankings** (`/ETFReport/ETFRank`):
   - Ingests monthly Top 20 ETF investor accounts for Core Fast-Track qualification.
6. **Yahoo Finance Global Big 5 Benchmarks & $R^2$ Regression**:
   - Ingests `^TWII` (TAIEX), `^GSPC` (S&P 500), `^NDX` (Nasdaq 100), `^SOX` (PHLX Semi), `^N225` (Nikkei 225).
   - Demonstrates Central Limit Theorem $N \ge 30$ trading day alignment and Ordinary Least Squares (OLS) $R^2$ regression calculation between `0050.TW` and `^TWII` ($R^2 \approx 0.9510$).
7. **U.S. Department of the Treasury XML Feed (Zero-Key Mode)**:
   - Polls official US government daily yield curve (`BC_10YEAR`, `BC_20YEAR`, `BC_30YEAR`).
   - Requires zero registration and zero API keys.

---

## 2. How to Run

```bash
# Run directly with python3 (no pip install required)
python3 docs/01-requirements/external-specs/samples/verify_external_feeds.py
```

---

## 3. Verified Execution Output Snapshot

```text
================================================================================
  AlphaHarvester External Data Feeds Verification & Probe
  Current Timestamp: 2026-09-22 17:53:48
================================================================================

[1/7] Probing TWSE ETF Master Universe (/opendata/t187ap47_L)...
      -> SUCCESS: Fetched 271 active funds/ETFs.
         Symbol: 0050 (元大台灣50) | Listed: 2003-06-30 | Benchmark: 臺灣50指數
         Symbol: 006208 (富邦台50) | Listed: 2012-07-17 | Benchmark: 臺灣50指數

[2/7] Probing TWSE Concentrated Market Daily Quotes (/exchangeReport/STOCK_DAY_ALL)...
      -> SUCCESS: Fetched 1380 securities.
         0050 (元大台灣50) -> Close: 111.35 TWD, Volume: 119,595,084 shares
         006208 (富邦台50) -> Close: 254.7 TWD, Volume: 2,458,921 shares

[3/7] Probing TPEx OTC Market Quotes (Bond ETFs) (/openapi/v1/tpex_mainboard_quotes)...
      -> SUCCESS: Fetched 118 OTC ETFs.
         00679B (元大美債20年) -> Close: 25.58 TWD, Volume: 9,786,000 shares

[4/7] Probing TWSE MIS Real-time NAV & Discount/Premium (all_etf.txt)...
      -> SUCCESS: Fetched 358 ETFs with live NAV.
         0050 (元大台灣50) -> Price: 111.85 | NAV: 111.83 | Discount/Prem: +0.02% | AUM: 24659.6 億 TWD
         006208 (富邦台50) -> Price: 255.7 | NAV: 255.9 | Discount/Prem: -0.08% | AUM: 4761.1 億 TWD
         0056 (元大高股息) -> Price: 56.85 | NAV: 56.7 | Discount/Prem: +0.26% | AUM: 7980.0 億 TWD
         00878 (國泰永續高股息) -> Price: 35.21 | NAV: 35.22 | Discount/Prem: -0.03% | AUM: 6616.9 億 TWD

[5/7] Probing TWSE Regular Quota (定期定額) Top 20 Rankings (/ETFReport/ETFRank)...
      -> SUCCESS: Fetched 20 ranked securities.
         Top 3 Regular Quota ETFs:
         Rank #1: 0050 元大台灣50 (1,280,028 accounts)
         Rank #2: 0056 元大高股息 (338,456 accounts)
         Rank #3: 00878 國泰永續高股息 (312,688 accounts)

[6/7] Probing Yahoo Finance Big 5 Benchmarks & 30-Day R^2 Linear Regression...
         Benchmark ^TWII  -> Latest Close: 47800.17
         Benchmark ^GSPC  -> Latest Close: 7764.70
         Benchmark ^NDX   -> Latest Close: 30482.35
         Benchmark ^SOX   -> Latest Close: 12433.17
         Benchmark ^N225  -> Latest Close: 65018.95
         [CLT Verification] 0050.TW vs ^TWII (65 trading days) -> R^2: 0.9510 (Core Fast-Track Target: >= 0.95)

[7/7] Probing U.S. Department of the Treasury XML Feed (Zero-Key Mode)...
      -> SUCCESS: Official U.S. Treasury Yields for Date 2026-09-21:
         10-Year Treasury Yield: 4.96%
         20-Year Treasury Yield: 5.33%
         30-Year Treasury Yield: 5.29%
         Estimated IG Corporate Bond Yield: 6.58%

================================================================================
  ALL EXTERNAL DATA INTEGRATION POINTS VERIFIED AND OPERATIONAL!
================================================================================
```

