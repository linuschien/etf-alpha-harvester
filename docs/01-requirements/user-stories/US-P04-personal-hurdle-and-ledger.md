# US-P04 模組：個人月度對帳月報、退休導航與歸因 (Personal Hurdle, MoM Ledger & Attribution)

## 背景 (Background)
本模組為 AlphaHarvester 個人投組層（Personal Services）之導航與戰情室核心。多數投資人每個月都要花費時間等待銀行與證券對帳單寄達，手動鍵入 Excel 試算淨值月增減與資產成長。本模組針對該痛點：
1. **全面取代 Excel**：自動生成資產淨值月報與 MoM（月增減）拆解分析；
2. 結合「由上而下（退休需求）」與「由下而上（資產組合期望）」之退休 Hurdle Rate 求解與蒙地卡羅財富漏斗錐；
3. 採用雙重報酬率（TWR vs MWR）與三源績效歸因；
4. 於 GCP Cloud Run 上原生整合 Google Cloud IAP 身分認證，確保個人數據極致安全隔離。

---

## US-P04-01：月度資產對帳月報與 MoM 月增減分析 (全面取代 Excel)

**身份**：投資人 (Investor)

> **As a** 投資人，  
> **I want to** 在每月對帳單寄達時不再需要手動更新 Excel，而是直接在系統查看自動生成的資產淨值月報與 MoM 月增減拆解，  
> **So that** 徹底擺脫手工記帳負擔，並清晰洞悉本月資產增減究竟來自自己的新儲蓄、市場波動還是配息貢獻。

### 驗收條件 (Acceptance Criteria)
- **AC1 (每月自動快照觸發)**：
  - 每月最後一個營業日收盤後，或使用者在當月校準交割戶現金水位後，系統自動生成一筆 `MonthlyPortfolioSnapshot`。
  - 記錄：`snapshot_month` (YYYY-MM)、`total_net_worth`（股票市值 + 債券市值 + 現金）、`equity_value`、`bond_value`、`free_cash`。
- **AC2 (MoM 月增減金額與增減率計算)**：
  $$\Delta V_{\text{total}} = V_{\text{current\_month}} - V_{\text{previous\_month}}$$
  $$\text{MoM \%} = \frac{\Delta V_{\text{total}}}{V_{\text{previous\_month}}} \times 100\%$$
- **AC3 (三向歸因拆解公式)**：
  系統自動將 $\Delta V_{\text{total}}$ 拆解為三大組成項目並於月報中並列展示：
  1. **本月新儲蓄注資 ($\Delta C_{\text{savings}}$)**：當月外部新轉入交割戶之本金總額。
  2. **市場資本利得/損益 ($\Delta P_{\text{market}}$)**：持倉市值變動與已實現資本利得之和。
  3. **配息收益入帳 ($D_{\text{dividends}}$)**：當月所有已入帳之 ETF 現金股利總和。
  *檢驗等式：$\Delta V_{\text{total}} = \Delta C_{\text{savings}} + \Delta P_{\text{market}} + D_{\text{dividends}}$ 必須完全恆等。*
- **AC4 (長線財富成長曲線圖)**：介面自動繪製月度資產階梯柱狀圖與淨值成長折線圖，支援自開戶以來的全歷史期間切換。

---

## US-P04-02：退休目標 Hurdle Rate 求解器 ＆ 蒙地卡羅財富漏斗錐

**身份**：投資人 (Investor)

> **As a** 投資人，  
> **I want to** 輸入我的退休目標終值與退休年限，由系統動態推導所需最低實質年化報酬率 (Hurdle Rate) 並繪製蒙地卡羅財富漏斗錐，  
> **So that** 我能隨時確認目前的累積進度是否落在安全航道內，避免臨近退休才發現資金缺口。

### 驗收條件 (Acceptance Criteria)
- **AC1 (由上而下 Hurdle Rate 求解)**：
  - 使用者輸入：目標退休終值 $V_{\text{target}}$（例如 3,000 萬 TWD）、退休剩餘年限 $T$（例如 20 年）、目前起始資產 $V_0$、每月儲蓄 $C_{\text{monthly}}$。
  - 系統以數值求解法（Newton-Raphson）求得所需最低實質年化報酬率 $r_{\text{req}}$：
    $$V_{\text{target}} = V_0(1 + r_{\text{req}})^T + \sum_{m=1}^{12T} C_{\text{monthly}} \left(1 + \frac{r_{\text{req}}}{12}\right)^{12T - m}$$
- **AC2 (策略可行性檢核 - Feasibility Gap)**：
  - 系統計算資產池客觀期望報酬率 $E[R_{\text{portfolio}}] \approx 8.5\% \sim 10.5\%$（核心 8.0% + 夏農收割 2.5% + 債券 5.5%）。
  - 若 $\Delta = E[R_{\text{portfolio}}] - r_{\text{req}} \ge 0$：判定「策略可行，航道健康」。
  - 若 $\Delta < 0$：介面跳出警示，明確計算若要達標每月需「增加儲蓄額」或「延後退休幾年」。
- **AC3 (蒙地卡羅財富漏斗錐 - Cone of Wealth)**：
  - 系統以幾何布朗運動執行 10,000 次路徑模擬，生成 $P_{10}$（悲觀）、$P_{50}$（中位數）、$P_{90}$（樂觀）財富漏斗軌跡。
  - 航道偏離狀態機 (`TrajectoryState`) 自動判定：
    - `AHEAD` ($\text{Gap} \ge +20\%$)：超前航道（綠燈），提示獲利鎖定。
    - `ON_TRACK` ($-10\% \le \text{Gap} < +20\%$)：正常航道（藍燈），維持定額紀律。
    - `LAGGING` ($-25\% \le \text{Gap} < -10\%$)：落後航道（黃燈），計算月儲蓄補貼額 $\Delta C$。
    - `CRITICAL` ($\text{Gap} < -25\%$ 連續 2 季)：脫軌危機（紅燈），觸發策略重審對話框。

---

## US-P04-03：雙重報酬率度量 (Unitized TWR vs MWR) ＆ 三源績效歸因

**身份**：績效分析師 (Performance Attribution Analyst)

> **As a** 投資人，  
> **I want to** 同時查閱消除存提款干擾的「時間加權報酬 (TWR)」與反映個人真實口袋獲利的「資金加權報酬 (MWR/XIRR)」，並檢視三源績效歸因，  
> **So that** 我能客觀證明夏農波動收割再平衡帶來的真實超額 Alpha 價值。

### 驗收條件 (Acceptance Criteria)
- **AC1 (Unitized TWR 計算)**：
  - 系統將投資組合淨值單元化（Unit Value），每次外部現金進出時重新切分次週期：
    $$\text{TWR} = \prod_{k=1}^{n} (1 + R_k) - 1$$
  - 與大盤加權指數 (0050 Benchmark) 進行同週期純策略回報對標。
- **AC2 (MWR / XIRR 計算)**：
  - 計入所有定期定額扣款與現金存提的精確日期與現金流，求解內部報酬率 $r_{\text{mwr}}$：
    $$\sum_{j} \frac{C_j}{(1 + r_{\text{mwr}})^{t_j / 365}} + \frac{V_{\text{current}}}{(1 + r_{\text{mwr}})^{t_{\text{end}} / 365}} = 0$$
- **AC3 (三源績效歸因模型)**：
  $$\text{Total Return} = \underbrace{\beta \cdot R_{\text{market}}}_{\text{大盤 Beta 貢獻}} + \underbrace{\alpha_{\text{shannon}}}_{\text{夏農波動收割 Alpha}} + \underbrace{R_{\text{carry}}}_{\text{防禦債券息收}}$$
  - 系統每日同步運算一組虛擬的「買入死抱到底 (Buy & Hold)」靜態對照組，兩者累積 TWR 之差額即為 $\alpha_{\text{shannon}}$，量化證明動態再平衡帶來的額外超額回報。

---

## US-P04-04：GCP Cloud Run 部署與 Google Cloud IAP 身分驗證整合

**身份**：雲端架構師 / 系統營運 (Cloud Architect / SecOps)

> **As a** 系統營運者，  
> **I want to** 將前後端系統打包部屬於 Google Cloud Run，並以 Google Cloud IAP (Identity-Aware Proxy) 作為統一身分驗證網關，  
> **So that** 使用者無需記憶任何密碼即可安全透過 Google Workspace/帳號單一登入，且個人數據獲得企業級保護。

### 驗收條件 (Acceptance Criteria)
- **AC1 (Cloud Run 容器化部屬)**：
  - 前端 React 與後端服務打包為符合安全標準之輕量 Docker 映像檔（以非 root 使用者執行）。
  - 符合無狀態容器規範，冷啟動時間 $< 3$ 秒。
- **AC2 (Cloud IAP 認證 Header 攔截)**：
  - 後端攔截器（Interceptor / Middleware）自動自 Incoming HTTP Request 解析 `X-Goog-Authenticated-User-Email`（格式如 `accounts.google.com:user@gmail.com`）。
  - 提取乾淨的 Email 作為當前請求的 `current_user_id`。
  - 若請求未攜帶合法的 IAP 簽名或處於非 IAP 環境（除本機開發模式外），後端一律回傳 `401 Unauthorized` 拒絕存取。
- **AC3 (多租戶資料隔離驗證)**：
  - 所有個人投組層資料庫查詢（Position, DcaSchedule, TradeTransaction, DividendTransaction 等）必須強制附加 `WHERE user_id = :current_user_id`，絕不允許跨租戶數據越權洩漏。

---

## US-P04-05：全域戰情室 Web 介面整合 (Web Dashboard)

**身份**：投資人 (Investor)

> **As a** 投資人，  
> **I want to** 登入首頁即可在一頁式的現代化戰情室中同時瀏覽宏觀狀態、停利告警、超跌雷達、月增減卡片與待確認扣款，  
> **So that** 我能在 3 分鐘內完成當月的資產巡檢與工單操作，享受極致的小白友善體驗。

### 驗收條件 (Acceptance Criteria)
- **AC1 (一頁式核心視圖劃分)**：
  1. **頂部狀態列 (Status Banner)**：宏觀利率狀態燈（高利蓄水/常態/低利收割）、當前 YTM 數值、守門員綠燈、IAP 使用者名稱。
  2. **核心資產卡片 (Asset Overview)**：總資產現值、本月月增減金額與 MoM %（新儲蓄/利得/配息拆解）、交割戶可用現金。
  3. **今日情報看板 (Daily Insights)**：
     - 若有停利標的：顯示綠色「停利收割訊號」卡片。
     - 若有超跌標的：顯示藍色「逢低加碼雷達」卡片。
     - 若有待確認扣款：顯示「扣款 5 秒微調入帳」卡片。
  4. **資產配置與排程 (Allocations & DCA Calendar)**：核心/衛星/債券圓餅圖、未來 30 天扣款日曆與資金缺口預告。
  5. **操作指引抽屜 (Action Drawer)**：若有低頻再平衡工單，點擊展開「先賣後買」清晰拆解操作清單，下單後提供一鍵確認成交按鈕。
- **AC2 (響應式設計)**：支援手機與平板垂直捲動無失真瀏覽，所有重要按鈕（如確認成交、校正庫存）觸控區域友善。
- **AC3 (零檔案匯出宣告)**：介面所有圖表均為即時 SVG/Canvas 渲染，不需要且不提供外部 PDF 或 JSON 匯出按鈕。

