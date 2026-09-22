# External Specification: Yahoo Finance Global Benchmarks & Yields

> **Document Version**: v1.0.0  
> **Status**: Verified & Production Ready  
> **Traceability**: [US-G01-03](../user-stories/US-G01-market-data-ingestion.md), [US-G02-01](../user-stories/US-G02-global-universe-ranking.md), [US-G02-02](../user-stories/US-G02-global-universe-ranking.md), [US-G03-01](../user-stories/US-G03-global-macro-yield.md)

---

## 1. Overview & Purpose

This specification defines the integration with Yahoo Finance v8 Chart API. This service provides:
1. **Global Big 5 Benchmark Daily Historical Data**:
   - `^TWII`: Taiwan Capitalization Weighted Stock Index (TAIEX)
   - `^GSPC`: S&P 500 Index (US Large Cap)
   - `^NDX`: NASDAQ 100 Index (US Tech / Growth)
   - `^SOX`: PHLX Semiconductor Index (US/Global Semiconductor)
   - `^N225`: Nikkei 225 Index (Japan Equities)
2. **Taiwan ETF Historical Time Series (30-Day & 60-Day Windows)**:
   - Used to compute Ordinary Least Squares (OLS) regression against benchmarks ($R^2$, $\beta$, tracking error) and annualized volatility ($\sigma$) for universe classification.
3. **Macro Treasury Yield & Corporate Bond Index Fallback**:
   - `^TNX` (10-Year US Treasury Note Yield)
   - `^TYX` (30-Year US Treasury Bond Yield)
   - `LQD` (iShares iBoxx $ Investment Grade Corporate Bond ETF)

---

## 2. Verified Endpoints & Interface Contracts

### 2.1 Yahoo Finance v8 Chart API

* **Base URL**: `https://query1.finance.yahoo.com/v8/finance/chart/{symbol}`
* **Method**: `GET`
* **Query Parameters**:
  - `interval`: `1d` (Daily bars)
  - `range`: `1mo`, `3mo`, `6mo`, `1y`, `5y`
  - `events`: `div,split` (Corporate actions: dividends and stock splits)
* **Headers**:
  ```http
  User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36
  Accept: application/json
  ```
* **Live Probe Result**: HTTP 200 verified across all Big 5 indices and Taiwan tickers.
* **Corporate Action Split Structure (Verified)**:
  ```json
  "events": {
    "splits": {
      "1718026200": {
        "date": 1718026200,
        "numerator": 10.0,
        "denominator": 1.0,
        "splitRatio": "10:1"
      }
    }
  }
  ```

#### Verified Live Response Sample (`0050.TW` & `^TWII`)
```json
{
  "chart": {
    "result": [
      {
        "meta": {
          "currency": "TWD",
          "symbol": "0050.TW",
          "exchangeName": "TAI",
          "fullExchangeName": "Taiwan",
          "instrumentType": "EQUITY",
          "regularMarketPrice": 111.85,
          "chartPreviousClose": 111.35,
          "exchangeTimezoneName": "Asia/Taipei"
        },
        "timestamp": [
          1789693200,
          1789952400,
          1790038800
        ],
        "indicators": {
          "quote": [
            {
              "open": [110.0, 110.5, 111.5],
              "high": [110.5, 111.5, 112.0],
              "low": [109.5, 110.0, 111.2],
              "close": [109.85, 111.35, 111.85],
              "volume": [11200000, 14500000, 12891394]
            }
          ],
          "adjclose": [
            {
              "adjclose": [109.85, 111.35, 111.85]
            }
          ]
        }
      }
    ],
    "error": null
  }
}
```

---

## 3. Supported Symbols & Classification Targets

| Category | Yahoo Ticker | Instrument Name | Primary Usage in AlphaHarvester |
| :--- | :--- | :--- | :--- |
| **Global Benchmark** | `^TWII` | TAIEX (台灣加權股價指數) | Benchmark for Taiwan Core & Broad Market ($R^2 \ge 0.95$) |
| **Global Benchmark** | `^GSPC` | S&P 500 Index | Benchmark for US Broad Market Core ETFs |
| **Global Benchmark** | `^NDX` | NASDAQ 100 Index | Benchmark for Tech / Growth Satellite ETFs |
| **Global Benchmark** | `^SOX` | PHLX Semiconductor Index | Benchmark for Semiconductor Satellite ETFs |
| **Global Benchmark** | `^N225` | Nikkei 225 Index | Benchmark for Japan Allocation Satellite ETFs |
| **Core ETF Fallback**| `0050.TW` | Yuanta Taiwan Top 50 ETF | Daily quote fallback & regression input |
| **Core ETF Fallback**| `006208.TW` | Fubon Taiwan FTSE 50 ETF | Daily quote fallback & regression input |
| **Macro Yield** | `^TNX` | CBOE 10-Year Treasury Yield | Macro yield state machine benchmark ($10 \times \text{Yield}$, e.g. $49.63 \to 4.963\%$) |
| **Macro Yield** | `^TYX` | CBOE 30-Year Treasury Yield | Macro yield state machine long-term spread |
| **Macro Corp Yield** | `LQD` | iShares Investment Grade Corp | US IG Corporate Bond proxy |

---

## 4. Time Series Alignment & Multi-Factor Math

### 4.1 Multi-Market Calendar Alignment
When calculating regression between a Taiwan ETF (`0050.TW`) and a foreign benchmark (e.g. `^GSPC` or `^N225`):
- US, Taiwan, and Japan exchanges have differing bank holidays.
- **Rule**: AlphaHarvester performs an **inner join on UTC dates** ($Date_{\text{ETF}} \cap Date_{\text{Index}}$).
- Only days where both instruments traded are included in the return series.
- $N$ must satisfy $N \ge 30$ aligned trading days.

### 4.2 Mathematical Formulas

1. **Daily Log Return**:
   $$r_{i, t} = \ln\left(\frac{P_{i, t}}{P_{i, t-1}}\right)$$

2. **OLS Linear Regression against Benchmark**:
   $$r_{\text{etf}, t} = \alpha + \beta \cdot r_{\text{index}, t} + \epsilon_t$$
   $$\beta = \frac{\text{Cov}(r_{\text{etf}}, r_{\text{index}})}{\text{Var}(r_{\text{index}})}$$

3. **Coefficient of Determination ($R^2$)**:
   $$R^2 = \frac{[\text{Cov}(r_{\text{etf}}, r_{\text{index}})]^2}{\text{Var}(r_{\text{etf}}) \cdot \text{Var}(r_{\text{index}})}$$
   * **Core Fast-Track Requirement**: $R^2 \ge 0.95$ (relative to `^TWII` or `^GSPC`).

4. **Annualized Historical Volatility ($\sigma$)**:
   $$\sigma = \sqrt{252} \times \sqrt{\frac{1}{N-1} \sum_{t=1}^{N} (r_{\text{etf}, t} - \bar{r})^2}$$
   * **Satellite High-Beta Requirement**: $\sigma \ge 18\%$.

---

## 5. Ingestion Schedule & Throttling Policies

1. **Unified Daily Sync Time**:
   - **Trigger Time**: Daily at 08:00 TST (00:00 UTC, Cron: `0 0 * * *`).
   - **Rationale**: At 08:00 TST, all three target regions (Taiwan, Japan, and US) and the US Treasury department have 100% completed clearing and settlement for the preceding 24-hour cycle. Single unified query covers all benchmarks (`^TWII`, `^GSPC`, `^NDX`, `^SOX`, `^N225`, `^TNX`).
2. **Rate Limiting & Headers**:
   - Random jitter between requests: 500ms ~ 1500ms.
   - User-Agent rotation: Standard modern desktop browser user-agents to avoid 429 Too Many Requests.
3. **Caching**:
   - Historical bars are permanently immutable once the trading day concludes.
   - AlphaHarvester stores daily bars in `global_daily_market_quotes`; only incremental new bars are requested.

