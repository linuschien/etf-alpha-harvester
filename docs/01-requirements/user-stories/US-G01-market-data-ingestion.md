# US-G01 模組：市場數據與情報基石 (Global Market Data & Intelligence)

## 背景 (Background)
本模組為 AlphaHarvester 系統全域層（Global Services）之核心基石。量化模型的所有計算（多因子評分、波動度求解、宏觀利率狀態機、除息入帳與持倉市值評估）均高度依賴即時、精確且完整的客觀市場資料。若數據缺失或錯誤，整個系統將完全無法運算。本模組負責自動化採集台股 ETF 日行情、FRED 美國公司債殖利率、證交所每月定期定額熱門排行、新上市 ETF 資料與除息日程，並透過「數據齊備性守門員」防禦髒資料污染下游。

> **外部技術規格參考 (External Interface Specifications)**：  
> 詳細上游 API 端點定義、即時連線實測驗證數據與欄位轉換格式，請參閱 [External Specs](../external-specs/README.md)。

---

## US-G01-01：台股 ETF 與全球五大市場基準指數每日收盤行情採集

**身份**：系統排程器 (System Scheduler)

> **As a** 系統排程器，  
> **I want to** 於每日台北時間 08:00 (UTC 00:00，Cron: `0 0 * * *`) 自動向臺灣證券交易所 (TWSE)、櫃買中心 (TPEx) OpenAPI 或 Yahoo Finance 拉取監控 ETF 以及全球五大市場基準指數的前一交易日官方定案收盤行情，  
> **So that** 系統擁有最新且準確的收盤價、日 K 線與成交量以支援後續市值計算、回歸擬合 ($R^2$) 與跨週期波動度分析。

### 驗收條件 (Acceptance Criteria)
- **AC1 (Happy Path - ETF 與五大基準指數)**：每日台北時間 08:00 (UTC 00:00)，系統觸發統一排程任務，針對納入監控的台股 ETF 之外，**強制同步採集全球五大市場基準指數**：
  1. `^TWII`：台灣發行量加權股價指數 (台股總體 Beta 基準、回撤監控線)
  2. `^GSPC`：美國標普 500 指數 (美股/全球廣基大盤基準)
  3. `^NDX`：美國那斯達克 100 指數 (全球科技巨頭與動能衛星基準)
  4. `^SOX`：美國費城半導體指數 (晶片與硬體科技領先循環基準，對標 00830)
  5. `^N225`：日本日經 225 指數 (亞洲成熟市場對標、日圓利差交易風險風向球)
  成功拉取包含 `ticker`、`trade_date` (YYYY-MM-DD)、`close_price`、`open_price`、`high_price`、`low_price` 與 `volume` 之數據，並寫入 `MarketDailyQuote` 資料表。
- **AC2 (歷史日 K 完整度驗證)**：系統需保存各監控標的與五大基準指數至少近 504 個交易日（約 2 年）之連續日 K 資料，以確保滾動年化波動度 ($\sigma_{252}$)、200 EMA 與 252 日回歸分析 ($R^2$, $\beta$, $\rho$) 可穩定計算。
- **AC3 (假日與非交易日跨市場處置)**：採集服務需分別識別台股、美股與日股之休市行事曆，單一市場休市不影響其他市場的定時拉取。
- **AC4 (重試與防斷線機制)**：若對外 API 請求遭遇逾時 (Timeout > 10s) 或 HTTP 5xx 錯誤，系統自動實施指數退避（Exponential Backoff）重試（最多 3 次，間隔 30s、60s、120s）；若重試全數失敗，記錄 `ERROR` 等級日誌並觸發守門員異常旗標。

---

## US-G01-02：FRED 美國宏觀利率指標定時採集

**身份**：系統排程器 (System Scheduler)

> **As a** 系統排程器，  
> **I want to** 每日定時向美國聖路易斯聯邦儲備銀行 FRED API 採集美國投資級公司債到期殖利率 (YTM) 與公債基準殖利率，  
> **So that** 系統宏觀利率狀態機能即時判定當前全球貨幣環境（高利蓄水期、常態平衡期或低利收割期）。

### 驗收條件 (Acceptance Criteria)
- **AC1 (Happy Path)**：每日美東時間 18:00（或收盤後），系統自動調用 FRED API 抓取以下關鍵經濟指標：
  1. `BAMLC0A0CM`：ICE BofA US Corporate Index Effective Yield（美國投資級公司債到期殖利率）。
  2. `DGS10`：美國 10 年期公債殖利率。
  3. `DGS20`：美國 20 年期公債殖利率。
- **AC2 (數據轉換與存檔)**：抓取之殖利率小數點需正確標準化為百分比數值（例如 5.25% 存為 `0.0525`），並連同發布日期寫入 `MacroYieldSnapshot` 資料表。
- **AC3 (節假日美股休市處置)**：若逢美股休市未發布新數值，系統自動沿用前一有效營業日之數值（Forward Fill），並於日誌標註為 `FORWARD_FILLED`。
- **AC4 (API 金鑰安全規範)**：FRED API Key 必須由 GCP Secret Manager 讀取，嚴禁硬編碼於程式碼或設定檔中。

---

## US-G01-03：證交所每月定期定額熱門排行月報採集

**身份**：系統排程器 (System Scheduler)

> **As a** 系統排程器，  
> **I want to** 於每月 15 日前定時採集臺灣證券交易所公告之「定期定額交易戶數 Top 20 ETF 標的與戶數月報」，  
> **So that** 系統可掌握市場散戶強共識標的與流動性支撐，作為模組 G-02 多因子評分之客觀加分項與戰情室情報。

### 驗收條件 (Acceptance Criteria)
- **AC1 (Happy Path)**：每月 11 至 15 日之間，系統定時向證交所公開資訊觀測站或 TWSE OpenAPI 抓取最新一期定期定額排行月報，解析前 20 大 ETF 之 `ticker`、`ranking_month`、`rank_position` (1~20) 與 `account_count` (交易戶數)。
- **AC2 (存檔與歷史趨勢)**：解析結果存入 `DcaPopularityRank` 資料表，支援依月份查詢各標的之戶數月增減與排名升降。
- **AC3 (公告延遲容錯)**：若證交所當月延遲公告，系統每日重試檢查一次，直到最新月份數據成功拉取為止，不影響日常行情運作。

---

## US-G01-04：新上市 ETF 基本面與除息日程採集 (滿 30 交易日自動納入監測)

**身份**：系統排程器 (System Scheduler)

> **As a** 系統排程器，  
> **I want to** 自動追蹤台股新掛牌 ETF 之基本面資料（代碼、指數、發行商、TER、AUM、除息日程與掛牌日期），並在掛牌滿 30 交易日後自動納入量化回歸監測，  
> **So that** 系統能以單一資產元資料模型動態掌握新上市 ETF，無需冗餘獨立資料表，並為後續客觀分類提供完整數據輸入。

### 驗收條件 (Acceptance Criteria)
- **AC1 (新上市 ETF 自動偵測與輕量元資料)**：系統定時比對 TWSE/TPEx 最新掛牌名單，凡新掛牌原型 ETF 自動登錄其 `ticker`、`name`、`listing_date` (掛牌日期)、`underlying_index`、`issuer` (發行投信)、`ter` (總費用率) 與最新 `aum`，直接保存於資產元資料，**不額外建立獨立暫存資料表**。
- **AC2 (掛牌天數動態計算與滿 30 交易日門檻)**：系統每日由 `CURRENT_DATE - listing_date` 動態計算掛牌天數；當實際累積收盤交易日數達 **$N \ge 30$ 個交易日** 時，系統自動啟動回歸資料採集標籤，納入模組 G-02 之月度分類與篩選管線。
- **AC3 (公開除息日程採集)**：每日檢索公開資訊觀測站之 ETF 配息公告，獲取包含 `ex_date` (除息日)、`payment_date` (發放日)、`dividend_per_share` (每股配息金額)，存入 `DividendAnnouncement`。
- **AC4 (標的分割與反分割事件採集 - Corporate Action Splits)**：
  - 系統每日 08:00 TST 定時自 Yahoo Finance API (`events=split`) 與證交所除權公告檢索標的分割資訊。
  - 一旦檢出分割事件，自動寫入 `CorporateAction` 表，記錄 `ticker`、`action_type` (SPLIT/REVERSE_SPLIT)、`effective_date` (生效日)、`split_ratio`、`numerator` 與 `denominator`。
  - 同時自動對該標的之歷史價格序列進行向後還原折算，寫入 `adj_close_price`，確保下游均線與波動度模型平滑過渡。

---

## US-G01-05：數據齊備性守門員 (Data Completeness Gatekeeper)

**身份**：守門員驗證器 (Gatekeeper Validator)

> **As a** 守門員驗證器，  
> **I want to** 在下游任何量化模組（多因子評分、宏觀狀態機、個人工單計算）啟動前執行嚴格的資料齊備性檢查，  
> **So that** 確保下游 100% 具備可信賴的真實數據，徹底杜絕因缺值或髒資料導致錯誤停利或錯誤委託。

### 驗收條件 (Acceptance Criteria)
- **AC1 (檢查指標覆蓋)**：守門員必須對當日排程檢查以下 3 項硬性指標：
  1. 所有個人持倉中標的與 G-02 候選標的，當日 `close_price` 必須存在且 $> 0$。
  2. FRED 最新 `us_ig_corp_ytm` 數值必須存在，且數值合理落在 $[0.01, 0.20]$ 區間（1% ~ 20%）。
  3. 各標的滾動 252 交易日之價格缺漏率嚴格 $= 0\%$。
- **AC2 (異常熔斷機制)**：若有任一檢查項未通過，守門員狀態標記為 `HALT`，強行阻擋下游模組執行，並於戰情室首頁顯示警示橫幅：「*市場數據採集未齊全，量化引擎已安全暫停，請稍後重試或檢查數據源*」。
- **AC3 (綠燈放行)**：若全數通過，守門員狀態標記為 `PASS`，釋放信號允許下游模組循序運算。

