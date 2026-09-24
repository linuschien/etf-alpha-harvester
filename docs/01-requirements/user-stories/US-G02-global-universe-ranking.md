# US-G02 模組：全域標的治理與多因子排名 (Global Universe & Factor Ranking Engine)

## 背景 (Background)
本模組為 AlphaHarvester 系統全域層（Global Services）之選品核心。投資的成功首先取決於資產池的品質，拒絕劣質、高內扣費用、流動性匱乏或每日槓桿耗損的標的。

本模組落實**「月度狀態感知 (Monthly State Refresh) ＋ 半年度決策執行 (Semi-Annual Decision Execution)」**架構：
1. **每月 15 日（配合證交所定期定額排行公告）**：系統自動刷新全市場標的分類（核心/衛星/債券/排除），並對新上市滿 30 個交易日（高斯中心極限定理 $N \ge 30$）之新標的執行「快速通道 (Fast-Track)」篩選，即時更新戰情室客觀候選池；
2. **每半年（6/30 與 12/31 收盤後）**：正式鎖定多因子總排名，驅動個人投組層 (P-01, P-02) 進行 1.4N 緩衝換倉與工單求解，兼顧市場新陳代謝敏銳度與低交易摩擦。

---

### US-G02-01：三大資產層級硬約束全域自動過濾 (一視同仁統一硬約束)

**身份**：量化評審引擎 (Quantitative Screening Engine)

> **As a** 量化評審引擎，  
> **I want to** 對全市場候選 ETF 施加統一的硬約束條件（Hard Constraints），  
> **So that** 系統能自動過濾次級標的，所有標的一視同仁憑客觀規模與流動性達標通過審查，無需特規通道。

### 驗收條件 (Acceptance Criteria)
- **AC1 (核心大盤 Core 判定基準與硬約束)**：
  - **判定基準**：近 30 交易日走勢回歸與三大市場指數 (TAIEX, S&P 500, NASDAQ) 之決定係數 $R^2 \ge 0.95$ 作為跟蹤大盤的分類判定依據（非硬約束）。
  - **硬約束條件**：
    1. 費用率優勢：總費用率 $\text{TER} \le 0.45\%$；
    2. 規模門檻：最新規模 $\text{AUM} \ge 100$ 億 TWD。
  - **淘汰規則**：未通過硬約束即判定不合格淘汰（`isQualified = false`），不另設平行淘汰規則。
- **AC2 (動能衛星 Satellite 硬約束過濾)**：
  - 聚焦特定高成長產業、關鍵資源或特定利基主題。
  - 日均成交金額 $> 2,000$ 萬 TWD。
  - 滾動實現年化波動度 $\sigma \ge 18\%$（具備足夠的夏農波動收割空間）。
  - **淘汰規則**：未通過硬約束即判定不合格淘汰（`isQualified = false`）。
- **AC3 (防禦債券 Defensive 硬約束過濾)**：
  - 標的必須為**現券型**投資級公司債或中天期公債 ETF（如 00720B、00725B 等）。
  - 信用評等必須 $\ge \text{BBB}$ 級。
  - 修正存續期間（Effective Duration）限制在 $8 \sim 14$ 年區間。
  - **嚴格無槓桿約束**：槓桿倍數必須嚴格 $= 1.0\times$，嚴禁任何每日重置之正 2 或反向型槓桿 ETF。
  - **淘汰規則**：未通過硬約束即判定不合格淘汰（`isQualified = false`）。
- **AC4 (核心標的永久豁免條款)**：符合核心大盤之標的，系統永久禁止生成主動全額清倉出清指令。

---

## US-G02-02：多因子量化評分與總排名 (月度感知 + 半年度決策)

**身份**：量化評審引擎 (Quantitative Screening Engine)

> **As a** 量化評審引擎，  
> **I want to** 每月自動更新標的客觀評分，並在每年 6/30 與 12/31 鎖定最終排名，  
> **So that** 系統能隨時保持敏銳的市場狀態感知，並在每半年以最具統計說服力的名次指引個人換倉。

### 驗收條件 (Acceptance Criteria)
- **AC1 (月度刷新 vs 半年度換倉排程)**：
  - **月度狀態刷新 (Monthly Refresh)**：每月 15 日 18:00（配合證交所定期定額排行公告），系統自動重算全市場各標的之最新得分與分類標籤，更新戰情室全域看板，但**不發布個人投組換倉指示**。
  - **半年度決策鎖定 (Semi-Annual Rebalance Execution)**：每年 6 月 30 日 18:00 與 12 月 31 日 18:00，系統以當日最新評分正式鎖定最終名次，觸發個人模組 P-01 的 1.4N 緩衝換倉判定。
- **AC2 (核心大盤評分公式計算)**：
  $$S_{\text{core}} = 0.35 \times \text{TER\_Score} + 0.25 \times \text{AUM\_Score} + 0.30 \times \text{TrackingError\_Score} + 0.10 \times \text{Spread\_Score}$$
- **AC3 (動能衛星評分公式計算 - 整合定期定額熱門排行與低相關性分散加分)**：
  $$S_{\text{sat}} = 0.30 \times \text{MOM} + 0.20 \times \text{Sharpe} + 0.20 \times \text{Hurst} + 0.15 \times (1 - \rho_{\text{core}}) \times 100 + 0.15 \times \text{DCARank\_Score}$$
  - $\rho_{\text{core}} = \sqrt{R^2}$：直接沿用近 30 交易日對大盤指數之決定係數計算結果，與大盤低相關性獲得更高分散性加分。
  - $\text{MOM}$：採過去 30 交易日動能報酬率換算得分。
  - $\text{DCARank\_Score}$：證交所定期定額戶數排行線性計分：
    $$S_{\text{dca}} = \begin{cases} (21 - r) \times 5.0, & r \in [1, 20] \\ 0.0, & \text{未進榜} \end{cases}$$
    （Top 1 為 100 分，Top 2 為 95 分，...，Top 20 為 5 分，未進榜為 0.0 分）。
- **AC4 (排名存檔)**：評分完成後，將每檔標的的細項得分與總名次寫入 `GlobalAssetScore`，標記計算月份與評審版本號。

