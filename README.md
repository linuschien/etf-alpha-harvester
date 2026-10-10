# ETF Alpha Harvester 跨週期自適應投資組合與幾何再平衡決策引擎

> **AlphaHarvester Cross-Cycle Adaptive Portfolio & Geometric Rebalancing Engine**  
> 專為台美股 ETF 投資人打造的跨週期配置、夏農幾何再平衡與全市場決策情報系統。

---

## 🌟 核心理念與解決痛點

傳統長期 ETF 投資人常面臨「假分散、真共線」與「追高殺低」的情緒偏誤：
1. **多標的共線冗餘**：同時持有 0050、006208、00757、00662 等高度重疊標的，不僅未達分散風險效果，反而增加交易成本與非系統性風險。
2. **缺乏跨週期宏觀指引**：無法即時感知升降息、再通膨或停滯通膨等總體經濟循環變化，難以動態調節核心與防禦配置比例。
3. **再平衡缺乏數學依據**：仰賴固定時間再平衡，忽視波動度與資產間幾何獨立性，錯失**夏農惡魔（Shannon's Demon）**的波動度幾何收割（Volatility Pumping）紅利。

**ETF Alpha Harvester** 透過四階段量化淘汰漏斗、多因子百分位評分與正交去共線演算法，為投資人建立客觀、可解釋且無情緒偏差的動態配置與再平衡決策支援。

---

## 🚀 目前已實現功能範圍：全市場決策情報 (Global Scope)

本系統目前已全面完成第一階段 **Global Scope（全市場宏觀情報與合規標的篩選）**，涵蓋三大情報面板與底層自動化資料管線：

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 ETF Alpha Harvester 決策情報大廳                                │
├───────────────────────────────┬─────────────────────────────────┬───────────────────────────────┤
│ 📊 總體經濟與市場情緒情報     │ 🎯 合規標的天梯榜與配置雷達     │ 📅 配息除息行事曆與台股定存   │
│ (Macro & Market Sentiment)    │ (Universe Leaderboard & Radar)  │ (Calendar & Liquidity)        │
└───────────────────────────────┴─────────────────────────────────┴───────────────────────────────┘
```

### 1. 總體經濟與市場情緒情報 (US-G03 / Tab 1)
* **4 大美債殖利率曲線監控**：即時整合 FRED 官方數據，包含 10 年期美債 (`DGS10`)、20 年期美債 (`DGS20`)、美銀高收益債券利差 (`BAMLC0A0CM`) 以及關鍵的 10Y-2Y 殖利率倒掛利差 (`T10Y2Y`)。
* **3 態景氣循環量化燈號**：依據實質利率與長短端利差動態識別總體景氣狀態：
  * 🟢 **金髮女孩經濟 (Goldilocks)**：溫和通膨、健康利差，利好大盤核心配置。
  * 🟡 **再通膨階段 (Reflation)**：經濟擴張、原物料升溫，衛星動能資產優先。
  * 🔴 **停滯通膨 / 赤字衝擊 (Stagflation / Deficit)**：倒掛或波動飆升，觸發防禦資產避險機制。
* **4 大恐慌情緒指標雙通道**：整合美股 VIX、那斯達克 VXN、CNN 恐懼與貪婪指數（Fear & Greed）、MOVE 債券波動率指數，具備警示/極端恐慌雙重通道標記。
* **5 大基準指數回撤與動態技術指標**：
  * 支援台股加權指數 (`^TWII`)、標普 500 (`^GSPC`)、那斯達克 100 (`^NDX`)、費城半導體 (`^SOX`) 與日經 225 (`^N225`)。
  * 內建 4 條移動平均線（20MA 月線、60MA 季線、120MA 半年線、240MA 年線）、布林通道（Bollinger Bands 2.0σ 雲帶）與斐波那契回撤黃金分割線（-23.6%, -38.2%, -50%, -61.8%, -76.4%）。

---

### 2. 合規標的天梯榜與正交決策雷達 (US-G02 / Tab 2)

#### A. 四階段量化篩選與分流漏斗 (Four-Stage Elimination Pipeline)

1. **Stage 0 (正則篩選)**：
   * 排除代碼含槓桿（`L`）、反向（`R`）及高風險結構型商品。
2. **Stage 1 (動態數值門禁與池間互斥分流)**：
   * **全局通用硬性門禁 (Universal Hard Gates)**（一票否決）：
     * **上市年資**：掛牌滿 365 個日曆天（滿 1 年），且近 365 日曆天內有效開盤交易日數 $N \ge 220$ 天。
     * **動態規模**：最新基金規模 $\text{AUM} \ge 20 \text{ 億 TWD}$（對接即時 NAV 與發行單位數，防範清算下市風險）。
     * **次級流動性**：近 30 個日曆天日成交金額中位數（30d Median Daily Turnover）$\ge 2,000 \text{ 萬 TWD}$（以中位數抵抗極端爆量，確保買賣滑價深度）。
   * **池間互斥分流門禁 (Mutual Exclusivity Gates)**：
     * **核心大盤 (Core)**：與台灣加權指數（`^TWII`）近 365 日曆天日報酬判定係數 $R^2_{\text{TAIEX}} \ge 0.90$（門禁落選直接除名，絕不下放衛星）。
     * **防禦債券 (Defensive)**：代碼以 `B` 結尾或名稱含「債」，且**一票否決非投資等級債（High Yield / 非投等債 / 高收益債）**，嚴守高信用避險屬性。
     * **動能衛星 (Satellite)**：排除核心標的與債券標的，近 90 個日曆天年化波動度 $\sigma_{90d} \ge 18\%$，且 12 個月累積動能 $\text{MOM}(12\text{M}) > 0$（純右側動能，低波高股息在此直接淘汰）。
3. **Stage 2 (各資產池多因子百分位數打分與排序)**：
   * 各池因子數與打分公式（Percentile Rank, 0 ~ 100 分）：
     * **核心大盤池 (Core Top 10，3 因子等權重)**：
       $$S_{\text{core}} = \left( \frac{1}{3}\text{Rank}(R^2_{\text{TAIEX}}) + \frac{1}{3}\text{Rank}(\text{DCA}) + \frac{1}{3}\text{Rank}(\text{AUM}) \right) \times 100$$
       * $\text{Rank}(R^2_{\text{TAIEX}})$：大盤同步純度百分位（近 365 日曆天與加權指數判定係數）。
       * $\text{Rank}(\text{DCA})$：證交所每月定期定額交易戶數百分位（Top 20 人氣標的給予正向得分，第 1 名 1.0、第 20 名 > 0，榜外 0.0）。
       * $\text{Rank}(\text{AUM})$：資產規模百分位（衡量造市深度與長期降費潛力）。
     * **動能衛星池 (Satellite Top 50，3 大動能品質因子)**：
       $$S_{\text{sat}} = \left( 0.50 \times \text{Rank}(\text{MOM}) + 0.25 \times \text{Rank}(\text{KER}) + 0.25 \times \text{Rank}(\text{Sharpe}) \right) \times 100$$
       * $\text{Rank}(\text{MOM})$（佔 50%）：12 個月累積價格動能 $[P(T) / P(T-365\text{d})] - 1$。
       * $\text{Rank}(\text{KER})$（佔 25%）：365 日考夫曼效率比（Kaufman Efficiency Ratio = $|\text{淨位移}| / \sum |\text{步長}|$），衡量價格推進平滑度，排除鋸齒假動能。
       * $\text{Rank}(\text{Sharpe})$（佔 25%）：近 365 日曆天純夏普值（$\text{Mean}(r)/\text{Std}(r) \times \sqrt{252}$），衡量風險調整後回報。
     * **防禦債券池 (Defensive Top 5，2 因子)**：
       $$S_{\text{bond}} = \left( 0.70 \times \text{Rank}(\text{YTM}) + 0.30 \times \text{Rank}(\text{AUM}) \right) \times 100$$
       * $\text{Rank}(\text{YTM})$（佔 70%）：近 1 年實質現金殖利率（Trailing 1-Year Cash Dividend Yield 百分位）。
       * $\text{Rank}(\text{AUM})$（佔 30%）：資產規模百分位（確保信用流動性緊縮時的造市深度）。
4. **Stage 3 (兩兩正交矩陣持久化與雙重視角決策引擎)**：
   * ⚡ **夏農幾何模式 (Shannon Geometric Harvest)**：點擊任一標的即切換為錨定種子（Click to Anchor），系統即時重新計算池內所有標的之正交入選（$R^2 < 0.50$）與共線排除（$R^2 \ge 0.50$）；欲還原預設狀態時直接點擊榜首第 1 名即可。
   * 🧩 **分群模式 (Clustering Mode)**：星狀拓撲（Star Topology）架構，以判定係數 $R^2 \ge 0.80$ 為同質門檻，貪婪分群展示各賽道領頭羊（Leader）及其同質替代標的（Alternatives），杜絕傳遞性鏈條污染。

#### B. 三態兩兩正交矩陣熱圖 (3-Tier Pairwise Heatmap Modal)
* 系統持久化保存核心組（最多 45 組）與衛星組（最多 1,225 組）之兩兩 $R^2$ 與相關係數 $\rho$ 矩陣，提供三態分類檢視：
  * 🟢 **夏農幾何候選 ($R^2 < 0.50$)**：正交獨立、低相關性，夏農收割最佳候選標的。
  * ⚪ **過渡中性區間 ($0.50 \le R^2 < 0.80$)**：中度相關，中性看待。
  * 🔴 **共線冗餘排除 ($R^2 \ge 0.80$)**：高度同質，視為重疊配置與冗餘剔除。
* 支援標的代碼搜尋即時高亮、行首標籤滾動置頂（Sticky Header）與點選單元格展開數值檢驗。

#### C. 標的深度決策透視抽屜與回檔加碼評分 (Asset Detail Drawer & Dip-Buy Engine)
* **類別專屬 Stage 2 多因子透視雷達**（直接對齊各資產類別之 Stage 2 評分維度，杜絕無意義通用指標）：
  * **核心大盤模型 (3 軸雷達)**：$R^2$ 基準貼合度 (33.3%)、DCA 定額人氣 (33.3%)、AUM 規模深度 (33.3%)。
  * **動能衛星模型 (3 軸雷達)**：12M 動能 MOM (50.0%)、KER 考夫曼效率比 (25.0%)、純夏普比率 Sharpe (25.0%)。
  * **防禦債券模型 (雙因子透視)**：YTM 實質到期殖利率 (70.0%)、AUM 規模深度安全墊 (30.0%)。
* **4 維度回檔加碼評分純函數 (Dip-Buy Opportunity Pure Evaluator - 總分 100 分)**：
  1. **布林通道極限折價 (%b，佔 30 分)**：基於 20MA 與 2.0σ 布林通道，計算當前價位相對位置 $\%b = \frac{Price - LowerBand}{UpperBand - LowerBand}$。$\%b \le 0.0$ 得 30 分、$\%b \le 0.15$ 得 20 分、$\%b \le 0.30$ 得 10 分。
  2. **斐波那契 52 週回撤深度 (佔 25 分)**：計算近 52 週（365 日曆天）高點回撤幅度。$|DD| \ge 38.2\%$ 得 25 分、$|DD| \ge 23.6\%$ 得 20 分、$|DD| \ge 14.6\%$ 得 12 分。
  3. **長天期均線支撐折價 (佔 25 分)**：檢驗 60MA (季線)、120MA (半年線) 與 240MA (年線) 支撐力道。跌破 240MA 深跌 $\le -5\%$ 得 25 分；跌破 120MA 或年線支撐得 18 分；跌破 60MA 或半年線支撐得 12 分。
  4. **全市場恐慌情緒 (VIX，佔 20 分)**：結合恐慌指數 $VIX \ge 35$ 得 20 分、$VIX \ge 30$ 得 15 分、$VIX \ge 25$ 得 8 分。
  * **星級加碼建議輸出**：
    * 🟢 **五星黃金坑 ($\ge 80$ 分，勝率 $\ge 90\%$)**：四維共振齊備，強烈建議調用閒置資金果斷單筆加碼。
    * 🟢 **四星超跌區 ($60 \sim 79$ 分，勝率 $75\% \sim 85\%$)**：具備高度安全邊際，建議分批佈局或提高定期定額額度。
    * 🟡 **三星平穩區 ($40 \sim 59$ 分，勝率 $60\% \sim 70\%$)**：常態健康回檔，維持日常定期定額紀律扣款。
    * ⚪ **觀望平靜期 ($< 40$ 分)**：無特定加碼動作建議。

---

### 3. 配息除息行事曆與台股定期定額排行 (US-G01 / Tab 3)
* **雙模態除息事件日曆**：
  * **月曆視圖 (Event Calendar)**：支援跨月懶加載與 3 級事件渲染優先權（🟣 股票分割 > 🟢 除息日 > 🔵 發放日）。
  * **表格視圖 (Table View)**：單行除息/發放日配對呈現，支援標的代碼文字精準搜尋（按事件日期排序，無天期篩選）。
* **TWSE 定期定額 Top 20 排行榜**：
  * 即時串接台灣證券交易所定期定額統計數據，客觀揭露熱門標的排名、代碼、名稱、配息週期與定期定額交易戶數（純客觀戶數排行，無年化殖利率或市價等衍生欄位）。

---

### 4. 自動化資料管線與健康狀態 (US-G01 Pipeline)
* **定時排程引擎**：每日 07:00 (Asia/Taipei) 自動觸發 `MarketDataSyncScheduler`，同步全市場 Yahoo Finance 與 FRED 數據。
* **資料完整性檢核機制 (Gatekeeper)**：嚴格驗證收盤價、成交量與指標連續性；遇休市自動對齊最新交易日；具備缺漏自我修復（Auto Gap Recovery）能力。
* **動態資料同步燈號膠囊**：
  * 🟢 **資料同步狀態**：全管線浮水印狀態皆為 `HEALTHY`/`SUCCESS`。
  * 🔴 **資料同步狀態**：任一關鍵資料源異常中斷或延遲。
  * 點擊即可開啟詳細浮水印檢視彈窗，掌握各市場最新更新時戳。

---

## 🏗️ 系統架構

本系統遵循嚴格的**洋蔥架構 / 六角架構 (Onion / Hexagonal Architecture)**，落實關注點分離：

```text
┌───────────────────────────────────────────────────────────────────────────────────┐
│                        前端展示層 (React 19 Declarative UI)                       │
│     @json-render/react  |  @json-render/shadcn  |  TanStack Query v5 (MSW Mock)   │
└────────────────────────────────────────┬──────────────────────────────────────────┘
                                         │ HTTP REST / GraphQL
┌────────────────────────────────────────▼──────────────────────────────────────────┐
│                   後端驅動層 (Inbound Adapters / Controllers)                    │
│        GlobalMarketIntelligenceController  |  MarketDataRestController            │
├───────────────────────────────────────────────────────────────────────────────────┤
│                   領域應用層 (Application Services / Use Cases)                   │
│   MarketIntelligenceService | FactorEvaluationService | MarketDataSyncScheduler   │
├───────────────────────────────────────────────────────────────────────────────────┤
│                     領域模型層 (Domain Core / Pure Logic)                         │
│     ShannonDecouplingEngine | DipBuyOpportunityService | RegimeClassifier         │
├───────────────────────────────────────────────────────────────────────────────────┤
│                   基礎設施層 (Outbound Adapters / Ports)                          │
│     YahooFinanceClient | FredClient | R2DBC Reactive Repositories | Flyway        │
└───────────────────────────────────────────────────────────────────────────────────┘
```

---

## 💻 技術棧 (Tech Stack)

### 前端 (Frontend)
* **核心框架**：React 19, TypeScript 5.7+, Vite 6
* **宣告式動態渲染**：`@json-render/react`, `@json-render/shadcn` (基於 JSON-Render Schema 驅動)
* **樣式與元件**：Tailwind CSS v4, Lucide React, Radix UI Primitives
* **狀態與非同步管理**：TanStack React Query v5, JSON-Render Unified StateStore
* **測試框架**：Vitest 3.0+, Testing Library, Mock Service Worker (MSW 2.7+)

### 後端 (Backend)
* **運行環境**：Java 25 (OpenJDK 25)
* **核心框架**：Spring Boot 4.0.8 (WebFlux 反應式非阻塞架構)
* **API 協定**：Spring REST / Reactive WebFlux, Spring GraphQL
* **資料持久化**：Spring Data R2DBC (響應式資料庫連線), Flyway Database Migrations
* **排程系統**：Spring Reactive Task Scheduler (Asia/Taipei 定時排程)

### DevOps & 容器化 (Containerization)
* **容器基底**：Eclipse Temurin 25 JRE Alpine (最小攻擊面、極致輕量化)
* **容器安全性**：強制宣告 `appuser:appgroup` (非 root 權限執行)
* **雲端平台**：Google Cloud Run (Zero-Downtime Rolling Update)
* **映像檔倉庫**：Google Artifact Registry (`pkg.dev`)

---

## 📂 專案目錄結構

```text
etf-alpha-harvester/
├── .agents/
│   └── skills/                      # 代理人工具與自動化腳本 (Docker build, Cloud Run deploy)
├── docs/
│   ├── 01-requirements/             # 產品規格需求說明書 (PRD, User Stories, Gherkin BDD)
│   └── 02-design-specs/             # 架構設計文件 (PlantUML 合約, DBML, OAS 3.2 規範)
├── engineers/
│   └── 03-implementations/
│       ├── frontend/                # React 19 + @json-render 前端專案
│       │   ├── src/
│       │   │   ├── json-render/     # 自訂圖表、熱圖、雷達圖擴充元件
│       │   │   ├── pages/           # 全市場決策情報主頁面與測試
│       │   │   └── schemas/         # 頁面宣告式 render-schema.json
│       │   └── package.json
│       ├── backend/                 # Spring Boot 4 + Java 25 WebFlux 後端專案
│       │   ├── src/main/java/       # 六角架構領域邏輯、控制器與排程
│       │   ├── src/main/resources/  # 應用程式配置與 Flyway 資料庫遷徙腳本
│       │   └── pom.xml
│       └── devops/                  # 容器化建置目錄 (輕量化 Dockerfile)
└── README.md
```

---

## 🛠️ 本地開發與測試指引

### 1. 前端開發環境

```bash
cd engineers/03-implementations/frontend

# 安裝相依套件
npm install

# 啟動 Vite 本地開發伺服器 (包含 MSW Mock API)
npm run dev

# 執行全數單元測試 (Vitest)
npm test

# 執行生產環境編譯打包 (TypeScript 檢查 + Vite Build)
npm run build
```

### 2. 後端開發環境

```bash
cd engineers/03-implementations/backend

# 執行後端單元測試與測試編譯
mvn test-compile
mvn test

# 啟動 Spring Boot 應用程式 (預設埠號 8080)
mvn spring-boot:run
```

---

## 🐳 容器化建置與雲端部署

本專案提供全自動化的腳本管線，可一鍵完成前端打包、後端 Fat-JAR 封裝、Docker 輕量化映像檔建置與 GCP Cloud Run 發布：

### 1. 本地 Docker 映像檔建置 (`build.sh`)
```bash
./.agents/skills/docker-image-builder/scripts/build.sh
```
* 自動編譯 React 前端靜態資產並注入 Spring Boot static 目錄。
* 執行 Maven 打包產出 Spring Boot Reactive Fat JAR。
* 產出標記有時間戳與 Git Commit SHA 的輕量化 Docker 映像檔 (`etf-alpha-harvester:YYYYMMDD-HHMMSS-sha`)。

### 2. 發布至 Google Cloud Run (`deploy.sh`)
```bash
./.agents/skills/cloud-run-deployer/scripts/deploy.sh
```
* 動態解析使用者的 `gcloud` 當前設定（Project ID 與 Region）。
* 自動設定 Artifact Registry 認證並推送最新映像檔。
* 觸發 Cloud Run 零停機滾動更新 (`gcloud run services update`)，保持現有環境變數與設定。

---

## 🗺️ 後續里程碑 (Next Roadmap)

* **Personal Scope (個人化投組管理 P-01 ~ P-04)**：
  * **P-01 投資人部位現況檢核**：串接券商庫存或自訂持有部位，試算即時真實曝險與集中度。
  * **P-02 跨週期自適應配置試算**：結合 Global Scope 景氣循環燈號，動態推薦核心與防禦權重。
  * **P-03 夏農幾何再平衡調倉引擎**：計算各標的目標增減股數與波動度幾何收割效益。
  * **P-04 模擬下單與調倉軌跡回測**：驗證跨週期回撤保護與 Alpha 累積成效。

---

## 📄 授權條款 (License)

Copyright © 2026 ETF Alpha Harvester Project. All rights reserved.
