# ETF Alpha Harvester — Known Issues & TODO 清單

最後更新日期：2026-11-04

---

## 總覽表

| # | 端別 | 嚴重度 | 議題名稱 | 觸發條件 / 現象 | 對系統的影響（副作用） | 目前狀態 |
| :-: | :-: | :-: | :--- | :--- | :--- | :-: |
| **1** | 後端 | 🔴 **高** | **月中新掛牌 ETF 觸發 Foreign Key 崩潰** | 月中新上市 ETF（如 `009831`）已出現在證交所收盤報價 API，但尚未同步進 `global_asset_metadata` 表 | `upsertDailyQuote` 寫入新代號時拋出 `CONSTRAINT_54` FK 例外，**腰斬後續所有櫃買債券 ETF（如 `00679B`）的報價寫入**，且 Watermark 卡在 `FAILED` | **保留**<br>*(待下次有新 ETF 上市時修復驗證)* |
| **2** | 後端 | 🟢 **低** | **突發颱風假 (`7/10`, `10/23`) 產生 ETF `vol=0` 平盤日 K** | 突發休市隔天觸發 `hasGap` 去 Yahoo 回補，Yahoo 對 ETF 回傳平盤 `vol=0`（`^TWII` 無資料） | `R`、`R²`、`MOM`、`KER`、`MDD`、`YTM` **零誤差**；`Vol 90d`、`Sharpe`、`30d 成交額` 影響 `< 1%` 且隨視窗推進自動消失 | **保留觀察** |
| **3** | 後端 | 🟢 **低** | **Yahoo 配息 `payment_date` 為推估值 & 指數 `volume` 單位不一** | Yahoo API 不提供配息發放日（目前用 `exDate + 30d`）；各國指數 `volume` 單位不一或恆為 `0` | `payment_date` 可能與真實發放日不同或落在週末；指數的 `volume` / `trade_value_twd` 僅存 DB 未被量化引擎與前端使用 | **保留備忘** |

---

## 各項細節與關鍵程式位置

### 1. [後端 🔴 高] 月中新掛牌 ETF 觸發 Foreign Key (`CONSTRAINT_54`) 崩潰
- **相關檔案與行號**：
  - [`CompositeExternalMarketDataAdapter.fetchTaiwanEtfDailyQuotes()`](engineers/03-implementations/backend/src/main/java/com/alphaharvester/adapter/out/external/CompositeExternalMarketDataAdapter.java#L102-L137)
  - [`MarketDataSyncService.syncTaiwanEtfQuotes()`](engineers/03-implementations/backend/src/main/java/com/alphaharvester/application/service/MarketDataSyncService.java#L268-L317)
- **詳細原因**：
  1. `TWSE_ETF_METADATA` 僅在每月初同步一次；當月中出現新掛牌 ETF（且代號符合 Stage 0 白名單 `^00\d{2,4}B?$`）時，證交所盤後 API `STOCK_DAY_ALL` 當天就會回傳該代號，但 `global_asset_metadata` 表尚無該代號紀錄。
  2. `primaryQuotes = Flux.concat(twseQuotes, tpexQuotes)` 未以已知的 `monitoredSet`（`monitoredTickers`）過濾，直接嘗試寫入 `market_daily_quote`，觸發外鍵 `CONSTRAINT_54` 例外。
  3. 因為 `Flux.concat` 是依序串流，一旦在 `twseQuotes` 中途拋錯中斷，排在該代號之後的上市 ETF 與**整批上櫃債券 ETF (`tpexQuotes`)** 在月底前每天都會被取消而漏寫入；同時外層 `.onErrorResume(...)` 吞掉例外導致 `@Transactional` 不會 Rollback 前半段已寫入的紀錄，且 `TAIWAN_ETF_QUOTES` Watermark 停留在 `FAILED`。
- **預計修復方向（待下次新 ETF 上市時實測驗證）**：
  - 在 `CompositeExternalMarketDataAdapter.fetchTaiwanEtfDailyQuotes` 中將 `primaryQuotes` 嚴格過濾於 `monitoredSet.contains(q.getTicker())` 內，或在偵測到未註冊新代號時先觸發增量 `syncMetadata`。

---

### 2. [後端 🟢 低] 突發颱風假 (`7/10`, `10/23`) 產生 ETF `vol=0` 平盤日 K
- **相關檔案與行號**：
  - [`SyncRangeUtil.resolveSyncWindow()`](engineers/03-implementations/backend/src/main/java/com/alphaharvester/application/util/SyncRangeUtil.java#L49-L78)
  - [`YahooFinanceClient.fetchTaiwanEtfHistoricalQuotes()`](engineers/03-implementations/backend/src/main/java/com/alphaharvester/adapter/out/external/YahooFinanceClient.java#L153-L165)
- **詳細原因與影響評估**：
  - 颱風假休市當天證交所/櫃買無報價，隔天觸發 `hasGap` 向 Yahoo Finance 回補時，Yahoo 會對台股 ETF 回傳 `close = 前日收盤價, volume = 0, trade_value_twd = 0` 的平盤 K 棒（但大盤 `^TWII` 無該日資料）。
  - 因 `alignReturnSeries` 取交集對齊日期，`R` 與 `R²` 零誤差；`MOM`、`KER`、`MDD`、`YTM` 亦零誤差；僅 `Vol 90d`、`Sharpe` 與 `30d 成交額中位數` 受單日 `0` 值微幅影響（`< 1%`），且隨滾動視窗推進自動消退。
- **預計修復方向（待有突發休市時驗證）**：
  - 在 `YahooFinanceClient.fetchTaiwanEtfHistoricalQuotes` 串流加上 `.filter(q -> q.getVolumeShares() != null && q.getVolumeShares() > 0)`。
  - ⚠️ **注意**：不可加在底層共用的 `fetchHistoricalQuotes`，因為大盤與恐慌指數（`^VIX`、`^VXN`、`^MOVE`）在 Yahoo Finance 的 `volume` 恆為 `0`。

---

### 3. [後端 🟢 低] Yahoo 配息 `payment_date` 為推估值 & 指數 `volume` 單位不一
- **相關檔案與行號**：
  - [`YahooFinanceClient.parseDividendsAndSplits()`](engineers/03-implementations/backend/src/main/java/com/alphaharvester/adapter/out/external/YahooFinanceClient.java#L241-L244)
  - [`YahooFinanceClient.fetchHistoricalQuotes()`](engineers/03-implementations/backend/src/main/java/com/alphaharvester/adapter/out/external/YahooFinanceClient.java#L101-L110)
- **詳細原因與影響評估**：
  - **配息發放日 (`payment_date`)**：Yahoo Finance v8 chart events 僅提供精確的除息日（`exDate`），不提供發放日，目前以 `exDate.plusDays(30)` 推估；後端量化引擎計算殖利率與配息頻率皆只使用 `exDate`，僅前端月曆/列表顯示此推估發放日。
  - **大盤指數 (`benchmark_index`) 的 `volume_shares`**：8 大基準指數（`^TWII`、`^GSPC`、`^NDX`、`^SOX`、`^N225`、`^VIX`、`^VXN`、`^MOVE`）在 Yahoo Finance 的 `volume` 單位不一或恆為 `0`；但系統對大盤指數僅讀取 `close_price`，不參與 ETF 的 2,000 萬成交額一票否決（台股 ETF 的成交量與成交額單位在全資料源皆 100% 一致為「股」與「新台幣元」）。

