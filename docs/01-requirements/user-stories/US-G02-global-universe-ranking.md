# US-G02 模組：全域標的治理與多因子排名 (Global Universe & Factor Ranking Engine)

## 背景 (Background)
本模組為 AlphaHarvester 系統全域層（Global Services）之選品與正交去共線核心。投資的成功首先取決於資產池的品質，拒絕劣質、高內扣費用、流動性匱乏或相互共線踩踏的標的。

本模組嚴格落實**「四階段篩選與評分體系 (Stage 0 ~ Stage 3)」**，並依據**「月度排程產出 (Monthly Cadence on the 1st) ＋ Watermark 斷路跳過 ＋ Admin 歷史覆蓋重算」**與**「半年度 7/1 與 1/1 個人換倉對齊」**之架構運作：
1. **每月 1 日月度排程產出機制**：每月 1 日 08:00 TST（或當月首次排程啟動時），系統以截至上月底之完整月度數據（如 9 月評分採用截至 8 月 31 日數據）產出當月 Top List，評審日期標記為當月 1 號（如 `2026-09-01`）。
2. **Watermark 斷路跳過機制**：利用 `data_feed_sync_watermark`（`feed_name = 'MONTHLY_TOP_LIST'`）記錄當月 Top List 是否已完成；每日例行排程在當月已完成時自動跳過計算。
3. **Admin API 覆蓋與歷史重算機制**：Admin 可隨時透過 API 指定過去月份（`yearMonth = YYYY-MM`）強制覆蓋重算該月之評分與正交矩陣。
4. **半年度個人換倉對齊**：個人投組層（P-01, P-02）之 1.4N 換倉決策直接對齊每年 7/1 與 1/1 產出之月度 Top List。

---

## US-G02-01：Stage 1 動態數值門禁與池間互斥分流 (Universal Gatekeepers & Mutual Exclusivity)

**身份**：量化評審引擎 (Quantitative Screening Engine)

> **As a** 量化評審引擎，  
> **I want to** 對通過 Stage 0 靜態正則阻斷的原型 ETF 施加全域通用硬性門禁與專屬類別互斥分流，  
> **So that** 系統能自動剔除年資不足、規模過小、流動性匱乏與動能下行的次級標的，且落實核心與衛星池間嚴格互斥，防止風格漂移。

### 驗收條件 (Acceptance Criteria)
- **AC1 (全局通用硬性門禁 - 通用於所有候選標的)**：
  通過 Stage 0 之原型標的必須同時滿足以下 3 項通用門禁，違者一票否決短路淘汰，不予評分，不寫入 `GlobalAssetScore`：
  1. **歷史資料長度充足**：掛牌上市 $\ge 365$ 個日曆天（滿 1 年），且近 365 個日曆天內有效交易日數 $N \ge 220$ 天。
  2. **最新資產規模充足**：最新動態規模 $\text{AUM} = \text{發行單位數} \times \text{最新 NAV} \ge 20$ 億 TWD。
  3. **次級市場流動性充足**：近 30 個日曆天平均日成交金額 $\ge 2,000$ 萬 TWD。
- **AC2 (核心大盤池專屬分流門禁與池間互斥鐵律)**：
  - **旗艦基準同步性**：標的近 365 個日曆天原始日報酬與成熟市場四大旗艦基準（TAIEX, S&P 500 [Shift-1], Nasdaq 100 [Shift-1], Nikkei 225 [^N225]）之最大判定係數滿足：
    $$\max(R^2_{\text{TAIEX}}, R^2_{\text{SP500}}, R^2_{\text{NDX100}}, R^2_{\text{N225}}) \ge 0.80$$
    （回歸計算以 365 日曆天內雙邊市場有效交易日交集為準；若相關係數 $\rho \le 0$ 強制歸零）。
  - **池間互斥鐵律**：通過此門禁之標的分流至【核心候選池】。若在後續 Stage 2 核心評分中落選，直接除名淘汰，**絕不下放至衛星池**。
- **AC3 (防衛債券池專屬分流門禁)**：
  - 標的代碼以 `B` 結尾或標的名稱含「債」。
  - 通過通用硬性門禁後，直接分流至【債券候選池】（依指示忽略信用評級門禁，維持實質現金殖利率客觀評估）。
- **AC4 (動能衛星池專屬分流門禁)**：
  - **非核心與非債券屬性**：排除核心大盤標的（$\max(R^2_{\text{bench}}) < 0.80$）與防衛債券標的。
  - **近一季波動能量硬性門禁**：近 90 個日曆天年化波動度 $\sigma_{90d} \ge 18\%$（$\sigma_{90d} = \text{近 90 日曆天日報酬標準差} \times \sqrt{252}$；$< 18\%$ 一票否決淘汰，秒殺低波高股息標的）。
  - **純右側動能一票否決**：$\text{MOM}(12-1) = [P(T - 30\text{d}) / P(T - 365\text{d})] - 1 > 0$（$\le 0$ 一票否決，杜絕接刀下行產業陷阱）。
- **AC5 (核心標的永久豁免條款)**：符合核心大盤之標的，系統永久禁止生成主動全額清倉出清指令。

---

## US-G02-02：Stage 2 各資產池多因子連續百分位打分與排名 (Percentile Ranking Engine)

**身份**：量化評審引擎 (Quantitative Screening Engine)

> **As a** 量化評審引擎，  
> **I want to** 對通過 Stage 1 分流之標的，在所屬資產池內進行名次導向之連續百分位數（Percentile Rank, 0 ~ 1）打分排序，  
> **So that** 系統消除極端離群值對權重的扭曲，各池主力因子均衡等權配置，客觀產出核心 Top 10、衛星 Top 20 與債券 Top 5 名單。

### 驗收條件 (Acceptance Criteria)
- **AC1 (排程週期、Watermark 與 Admin 歷史重算)**：
  - **每月 1 日排程產出機制**：每月 1 日 08:00 TST（或當月首次排程啟動時），系統以截至上月最後一日之歷史數據（如 9 月評分採用截至 8 月 31 日數據）產出當月 Top List，評審日期標記為當月 1 號（`evaluation_date = YYYY-MM-01`）。DCA 沿用當前最新已有之期數。
  - **Watermark 斷路跳過**：當月 Top List 產出成功後，標記 `data_feed_sync_watermark`（`feed_name = 'MONTHLY_TOP_LIST'`, `last_successful_sync_date = YYYY-MM-01`）。後續日常每日排程喚醒時，若比對當月已標記完成，自動**跳過** Top List 計算。
  - **Admin API 覆蓋與歷史月份重算**：管理員可透過端點（`POST /api/v1/globalAssetScores:evaluate?yearMonth=YYYY-MM`）強制重新計算。由 Admin 觸發時略過 Watermark 檢查；若傳入過去月份（如 `yearMonth = "2026-08"`），系統以該月歷史窗口重算並覆寫該月份之評分與正交矩陣。
  - **半年度換倉對齊**：個人投組層（P-01）之 1.4N 換倉直接以每年 7/1 與 1/1 產出之月度 Top List 執行。
- **AC2 (核心大盤池評分公式計算 - 產出 Core Top 10)**：
  $$S_{\text{core}} = \frac{1}{3}\text{Rank}(R^2_{\text{bench}}) + \frac{1}{3}\text{Rank}(\text{DCA}) + \frac{1}{3}\text{Rank}(\text{AUM})$$
  - $\text{Rank}(R^2_{\text{bench}})$：與通過門禁所對應基準指數近 365 日曆天 $R^2$ 之百分位排名。
  - $\text{Rank}(\text{DCA})$：證交所每月定期定額交易戶數百分位排名（定期定額 Top 20 標的皆有正向得分，第 1 名 1.0、第 20 名 > 0，榜外標的戶數為 0，得分 0.0）。
  - $\text{Rank}(\text{AUM})$：最新動態資產規模百分位排名。
  - 取前 10 名進入「核心 Top 10」，供 Stage 3 正交去共線。
- **AC3 (動能衛星池評分公式計算 - 產出 Satellite Top 20)**：
  $$S_{\text{sat}} = \left( \frac{1}{3}\text{Rank}(\text{MOM}) + \frac{1}{3}\text{Rank}(\text{KER}) + \frac{1}{3}\text{Rank}(\text{Sharpe}) \right) \times (1 - R^2_{\text{TAIEX}})$$
  - $\text{Rank}(\text{MOM})$：12-1 月經典動能 $\text{MOM}(12-1) = [P(T - 30\text{d}) / P(T - 365\text{d})] - 1$ 百分位排名。
  - $\text{Rank}(\text{KER})$：365 日曆天考夫曼效率比 $\text{KER} = \frac{|P(t) - P(t-365\text{d})|}{\sum_{i} |P(i) - P(i-1)|}$ 百分位排名。
  - $\text{Rank}(\text{Sharpe})$：近 365 日曆天純年化夏普值 $\text{Sharpe} = \frac{\text{Mean}(r)}{\text{Std}(r)} \times \sqrt{252}$（不扣除無風險利率，$r_f = 0$）百分位排名。
  - $(1 - R^2_{\text{TAIEX}})$：台股大盤影子股折價係數（近 365 日曆天原始日報酬判定係數）。
  - 取前 20 名進入「衛星 Top 20」，供 Stage 3 正交去共線。
- **AC4 (防衛債券池評分公式計算 - 產出 Bond Top 5)**：
  $$S_{\text{bond}} = 0.70 \times \text{Rank}(\text{YTM}) + 0.30 \times \text{Rank}(\text{AUM})$$
  - $\text{Rank}(\text{YTM})$：近一年實質現金殖利率（Trailing 1-Year Cash Dividend Yield）百分位排名。
  - $\text{Rank}(\text{AUM})$：資產規模百分位排名。
  - 取前 5 名進入「債券 Top 5」，系統直接選取 Rank 1 標的納入投組配置，不進行 Stage 3 正交去共線。
- **AC5 (評分表純淨原則)**：評分完成後，僅將**通過 Stage 1 門禁之合格標的**細項得分與組內名次（`class_rank = 1, 2, 3...`）寫入 `GlobalAssetScore`。未通過門禁者於日誌留痕，絕不寫入評分資料表。

---

## US-G02-03：Stage 3 兩兩正交矩陣持久化與通用貪婪正交去共線演算法 (Greedy Orthogonal Engine)

**身份**：正交決策引擎 (Orthogonal Decision Engine)

> **As a** 正交決策引擎，  
> **I want to** 計算並持久化保存 Top 候選標的的兩兩判定係數矩陣，並執行全量貪婪正交去共線標示（支援預設全局模式 A 與自訂種子錨定模式 B），  
> **So that** 系統徹底消除核心與衛星池內的共線重疊風險，全量輸出 `ACCEPTED` 與 `REJECTED_COLLINEAR` 狀態，落實寧缺勿濫。

### 驗收條件 (Acceptance Criteria)
- **AC1 (兩兩正交矩陣計算與持久化儲存 - Pairwise Matrix Persistence)**：
  系統對 Stage 2 產出之 Top 標的執行全量兩兩近 365 日曆天原始日報酬判定係數 $R^2$ 與相關係數 $\rho$ 運算，持久化寫入 `global_asset_pairwise_matrix` 資料表：
  1. 核心大盤池：計算 $10 \times 9 = 90$ 筆矩陣資料。
  2. 動能衛星池：計算 $20 \times 19 = 380$ 筆矩陣資料。
  每筆記錄包含 `evaluation_date`、`asset_class`、`base_ticker`、`target_ticker`、`r_squared` 與 `correlation_coefficient`。
- **AC2 (數理正交判定標準)**：
  兩兩標的之原始日報酬判定係數必須滿足：
  $$R^2(\text{Candidate}, \text{Selected}) < 0.50$$
  若皮爾森相關係數 $\rho \le 0$，判定係數強制歸零安全放行。
- **AC3 (模式 A：預設全局貪婪正交)**：
  1. **初始種子**：自動鎖定 Stage 2 綜合評分 Rank 1 標的為 Seed，標記為 `ACCEPTED` 成為初始已選集合 $\mathcal{S} = \{\text{Rank 1}\}$。
  2. **向下遍歷**：由 Rank 2 依序審查至名單末端。若當前候選標的與 $\mathcal{S}$ 中「所有已選標的」之兩兩 $R^2$ 均 $< 0.50$，標記為 `ACCEPTED` 並加入 $\mathcal{S}$；若與 $\mathcal{S}$ 中任一已選標的 $R^2 \ge 0.50$，標記為 `REJECTED_COLLINEAR`，並記錄衝突來源標的代碼與數值。
- **AC4 (模式 B：自訂種子錨定重算)**：
  1. **種子約束**：使用者僅可自 Stage 2 的 Top 清單中指定任一標的 S 為錨定種子。
  2. **強制置頂**：種子 S 優先入選，標記為 `ACCEPTED` 成為初始已選集合 $\mathcal{S} = \{S\}$。
  3. **依序遍歷**：清單中其餘標的嚴格按照 Stage 2 原始綜合評分由高至低依序比對正交條件，滿足者標記 `ACCEPTED`，衝突者標記 `REJECTED_COLLINEAR`。
- **AC5 (全量標示與自然飽和終止)**：
  1. 演算法不設固定截斷檔數 $k$，對 Top 10 核心與 Top 20 衛星全量標示每一檔的審查結果。
  2. 若向下遍歷無標的滿足 $R^2 < 0.50$，演算法自然飽和終止，嚴禁為湊足檔數而放寬門檻。
  3. 模式 A 之正交結果與衝突明細同步持久化於 `GlobalAssetScore` 表之 `orthogonal_status` 與 `collision_detail` 欄位。


