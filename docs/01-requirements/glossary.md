# AlphaHarvester 領域術語表 (Domain Glossary)

本文件為 AlphaHarvester 系統全生命週期的**領域術語唯一權威字典 (Source of Truth)**。下游架構師（Domain Modeler, Contract Generator）、工程師（Backend, Frontend）與測試人員嚴格禁止自行發明同義詞或混用術語。

---

## 1. 架構分層與參與者術語 (Architecture & Actors)

| 術語 | 英文代碼 | 定義與約束 |
| --- | --- | --- |
| **系統全域層** | `Global Services` | 處理市場行情、公開資訊、全市場多因子客觀排名與宏觀狀態等無狀態/共享邏輯的服務層。 |
| **個人投組層** | `Personal Services` | 處理個人資產庫存、現金流、自訂扣款日、再平衡工單與退休導航的隔離服務層，以使用者識別碼完全隔離。 |
| **投資人 / 小白使用者** | `Investor / User` | 系統的終端操作者，透過瀏覽器存取雲端戰情室。 |
| **IAP 身分識別碼** | `IAP User Identity` | 由 Google Cloud IAP 注入之 HTTP Header `X-Goog-Authenticated-User-Email`，作為多租戶資料隔離主鍵。 |

---

## 2. 核心領域實體與值物件 (Core Domain Entities & Value Objects)

### 2.1 系統全域層實體 (Global Entities - Clean Code 正名)

| 實體 / 物件 | 英文代碼 | 定義 | 關鍵屬性 |
| --- | --- | --- | --- |
| **全域基準指數** | `BenchmarkIndex` | 全球 9 大市場行情、波動度恐慌與綜合情緒基準指標。 | `ticker` (^TWII, ^GSPC, ^NDX, ^SOX, ^N225, ^VIX, ^VXN, ^MOVE, FEAR_GREED), `name`, `region`, `description` |
| **市場行情快照** | `MarketDailyQuote` | 單一標的或基準在特定交易日的市場成交價量、淨值與折溢價（純客觀成交事實，無除息還原價）。 | `ticker`, `trade_date`, `open_price`, `high_price`, `low_price`, `close_price`, `volume_shares`, `trade_value_twd`, `net_asset_value`, `discount_premium_percentage` |
| **宏觀殖利率快照** | `MacroYieldSnapshot` | FRED API 定時拉取之美國公司債與公債殖利率事實（資料庫純資料化，無狀態旗標）。 | `record_date`, `us_corporate_bond_effective_yield`, `us_10_year_treasury_yield`, `us_20_year_treasury_yield`, `yield_spread_10y_minus_2y` |
| **全域標的評分記錄** | `GlobalAssetScore` | 模組 G-02 每月/每半年對各組候選標的進行 Stage 2 客觀多因子百分位評分、組內獨立排名，以及 Stage 3 模式 A 正交標記。 | `ticker`, `evaluation_date`, `asset_class` (`CandidateAssetClass`), `class_rank`, `composite_score`, `fund_size_twd`, `orthogonal_status` (`OrthogonalStatus`), `collision_detail` |
| **兩兩正交矩陣記錄** | `GlobalAssetPairwiseMatrix` | 模組 G-02 持久化儲存之 Top 候選標的兩兩近 365 日曆天原始日報酬判定係數 $R^2$ 與相關係數 $\rho$。 | `evaluation_date`, `asset_class`, `base_ticker`, `target_ticker`, `r_squared`, `correlation_coefficient` |
| **定期定額熱門排行** | `DcaPopularityRank` | 臺灣證交所每月公告之定期定額交易戶數排行（年份與月份獨立）。 | `ticker`, `ranking_year`, `ranking_month`, `rank_position`, `regular_investor_count` |
| **全域標的元資料** | `GlobalAssetMetadata` | 標的基本檔案資料，由 `listing_date` 動態推算掛牌天數，收錄法定配息週期。 | `ticker`, `name`, `listing_date`, `underlying_index`, `fund_size_twd`, `asset_class` (`CandidateAssetClass`), `distribution_frequency` (`DistributionFrequency`) |
| **除息公告資訊** | `DividendAnnouncement` | 發行投信公開公告之 ETF 每期除權息日程。 | `ticker`, `ex_date` (除息日), `payment_date` (發放日), `dividend_per_share`, `tax_tag` (`OVERSEAS_76W` / `DOMESTIC_54C`) |
| **標的分割與除權事件** | `CorporateAction` | 標的分割與反分割事件，採整數除法架構徹底消除浮點數 1 股帳差。 | `ticker`, `action_type` (SPLIT/REVERSE_SPLIT), `effective_date`, `split_from_shares`, `split_to_shares` |

### 2.2 個人投組層實體 (Personal Entities)

| 實體 / 物件 | 英文代碼 | 定義 | 關鍵屬性 |
| --- | --- | --- | --- |
| **個人持倉** | `PersonalPosition` | 使用者實際持有的單一標的現況（極簡模型）。 | `user_id`, `ticker`, `asset_class` (`PositionAssetClass`), `total_shares`, `avg_cost_price`, `holding_since_date` (起扣日/時間錨點), `current_price` |
| **定期定額扣款排程** | `DcaSchedule` | 單一標的之獨立自動扣款設定。 | `user_id`, `ticker`, `dca_days` (扣款日陣列), `dca_amount` (約定扣款額), `dca_status` |
| **再平衡執行工單** | `RebalanceWorkOrder` | 工單求解器產出之單筆整股交易指令（整張優先、避開零股）。 | `order_id`, `user_id`, `ticker`, `action` (BUY/SELL), `round_lots` (張), `odd_shares` (股), `priority` |
| **交易成交流水帳** | `TradeTransaction` | 使用者確認成交之買賣記錄與期初開帳現金流（供 XIRR 精準計算）。 | `tx_id`, `user_id`, `ticker`, `action` (BASELINE / DCA_BUY / DIP_BUY / CALIBRATION_ADJUSTMENT / PROFIT_SELL / ORPHAN_SELL), `shares`, `price`, `amount` (現金流), `fees`, `tax`, `realized_gain` |
| **已實現配息收益記錄** | `DividendTransaction` | 除息發放日自動結算入帳之配息流水帳。 | `div_id`, `user_id`, `ticker`, `payment_date`, `shares_held`, `dividend_per_share`, `total_dividend`, `tax_tag` |
| **持倉審計事件流水** | `PositionAuditEvent` | 因標的分割、反分割或手動微調校正產生之持倉變更紀錄。 | `event_id`, `user_id`, `ticker`, `event_type` (SPLIT/CALIBRATION), `old_shares`, `new_shares`, `old_avg_cost`, `new_avg_cost`, `cost_delta`, `event_date` |
| **資產淨值月快照** | `MonthlyPortfolioSnapshot` | 每月總資產淨值與增減變動記錄（取代 Excel）。 | `snapshot_month`, `total_net_worth`, `equity_value`, `bond_value`, `free_cash`, `mom_change_amount`, `mom_change_pct` |

---

## 3. 領域列舉值與狀態機 (Domain Enums & State Machines)

### 3.1 宏觀利率狀態 (`MacroState` - 記憶體純函數運算)
| 列舉值 | 英文代碼 | 判定條件 | 系統處置行為 |
| --- | --- | --- | --- |
| **高利蓄水期** | `STATE_1_ACCUMULATE` | 美國投資級公司債有效殖利率 $> 5.0\%$ | 債券維持積極定額扣款；衛星利潤 30% 注水債券；建議股債比 80% : 20%。 |
| **常態平衡期** | `STATE_2_NEUTRAL` | $4.0\% \le \text{殖利率} \le 5.0\%$ | 債券停止續扣；債息全額回填股票；建議股債比 85% : 15%。 |
| **低利收割期** | `STATE_3_HARVEST` | 美國投資級公司債有效殖利率 $< 3.5\%$ | 債券觸發停利不續扣；分批出清債券賺取價差；100% 抄底核心股票；建議股債比 95% : 5%。 |

### 3.2 候選池分組與持倉狀態 (`CandidateAssetClass` vs `PositionAssetClass`)
| 列舉範圍 | 代碼與所屬分組 | 業務定義 |
| --- | --- | --- |
| **全域候選池**<br>`CandidateAssetClass` | **`CORE` (核心大盤)** | 長期資產基石，成熟市場旗艦基準 $R^2 \ge 0.80$（產出 Core Top 10），永久豁免主動出清，落選絕不下放衛星池。 |
| **全域候選池**<br>`CandidateAssetClass` | **`SATELLITE` (動能衛星)** | 排除核心與債券，近一季 $\sigma_{90d} \ge 18\%$ 且 $\text{MOM}(12-1) > 0$（產出 Sat Top 20），提供夏農波動收割超額利潤。 |
| **全域候選池**<br>`CandidateAssetClass` | **`DEFENSIVE` (防禦債券)** | 現券型投資級公司債/公債 ETF（產出 Bond Top 5，Top 1 直接入選），鎖定高息防禦墊。 |
| **個人專屬狀態**<br>`PositionAssetClass` | **`ORPHAN` (孤兒標的)** | **全域候選池嚴格排除**！僅當個人持有之舊標的跌出半年度 1.4N 緩衝區外時於個人層賦予，系統停止續扣並排定優先出清。 |

### 3.3 正交審查狀態 (`OrthogonalStatus`)
| 列舉值 | 英文代碼 | 業務定義與處置行為 |
| --- | --- | --- |
| **正交入選** | `ACCEPTED` | 該標的與已選集合中所有標的兩兩原始日報酬 $R^2 < 0.50$（或為初始種子），成功自然正交入選。 |
| **共線排除** | `REJECTED_COLLINEAR` | 該標的與已選集合中至少一檔標的 $R^2 \ge 0.50$，觸發共線阻斷淘汰，詳細記載衝突來源標的代碼與數值。 |

### 3.4 扣款排程狀態 (`DcaScheduleStatus`)
| 列舉值 | 英文代碼 | 業務定義 |
| --- | --- | --- |
| **正常扣款中** | `ACTIVE` | 每月依約定扣款日由券商自動扣款。 |
| **暫停扣款** | `PAUSED` | 系統自動暫停扣款（如債券進入低利收割期、或標的被標記為 ORPHAN 時）。 |

### 3.5 工單執行優先序 (`OrderPriority`)
| 列舉值 | 英文代碼 | 優先序等級 | 業務說明 |
| --- | --- | --- | --- |
| **第一優先（出清/停利賣單）** | `SELL_FIRST` | 1 | 優先出清孤兒標的與衛星停利賣單，先釋出交割款。 |
| **第二優先（核心回填買單）** | `BUY_CORE` | 2 | 確保有足夠現金後，執行核心大盤補缺買單。 |
| **第三優先（債券蓄水買單）** | `BUY_DEFENSIVE` | 3 | 高利蓄水期之防禦債券單筆買單。 |

### 3.6 退休航道偏離狀態 (`TrajectoryState`)
| 列舉值 | 英文代碼 | 條件門檻 | 系統診斷與指引 |
| --- | --- | --- | --- |
| **超前航道** | `AHEAD` | $\text{Gap} \ge +20\%$ | 資產累積超前預期，提示防守獲利鎖定。 |
| **正常航道** | `ON_TRACK` | $-10\% \le \text{Gap} < +20\%$ | 落在蒙地卡羅 $P_{10} \sim P_{90}$ 區間，維持紀律不變。 |
| **落後航道** | `LAGGING` | $-25\% \le \text{Gap} < -10\%$ | 落在 $P_{10} \sim P_{25}$ 區間，提示每月額外補貼儲蓄額 $\Delta C$。 |
| **脫軌警告** | `CRITICAL` | $\text{Gap} < -25\%$ (連續 2 季) | 跌破 $P_{10}$，觸發策略檢核會話，強制調降終值或延後退休。 |

---

## 4. 量化參數與數學符號表 (Quantitative Notation & Constants)

| 符號 / 代碼 | 預設值 / 單位 | 定義與業務語意 |
| --- | --- | --- |
| $\text{Raw Close}$ | 價格 (TWD) | 交易所原始未還原收盤價，所有日報酬、波動度、動能與 $R^2$ 回歸之強制計算基準（嚴禁 Adjusted Close）。 |
| $\text{Calendar Days}$ | 天數 | 全時間維度統一窗口：365 日曆天（長期/回歸）、90 日曆天（季波動度）、30 日曆天（月流動性）。 |
| $\sigma_{90d}$ | 百分比 (%) | 近 90 個日曆天滾動年化實現波動度（有效日報酬標準差 $\times \sqrt{252}$），衛星門禁門檻 $\ge 18\%$。 |
| $\text{MOM}(12-1)$ | 百分比 (%) | 12-1 月經典動能：$[P(T-30\text{d}) / P(T-365\text{d})] - 1$，剔除近 30 天短線走勢，衛星門禁門檻 $> 0$。 |
| $\text{KER}$ | 數值 $[0, 1]$ | 365 日曆天考夫曼效率比：$\frac{|\text{淨位移}|}{\text{總路徑長度}} = \frac{|P(t) - P(t-365\text{d})|}{\sum |P(i) - P(i-1)|}$，衡量推進平滑度。 |
| $\text{Sharpe}$ | 數值 | 近 365 日曆天純年化夏普值：$\frac{\text{Mean}(r)}{\text{Std}(r)} \times \sqrt{252}$（不扣無風險利率，$r_f = 0$）。 |
| $\text{Rank}(X)$ | 數值 $[0, 1]$ | Percentile Rank 連續變數百分位數排名，各池主力因子組內名次歸一化打分。 |
| $R^2_{\text{orthogonal}}$ | 0.50 | 兩兩原始日報酬判定係數互斥門檻（等價高維空間夾角 $\theta > 45^\circ$；$\rho \le 0$ 強制歸零安全放行）。 |
| $C_{\text{monthly}}$ | 變數 (TWD) | 使用者每月新增儲蓄投入金額。 |
| $C_{\text{min\_lot}}$ | 3,000 TWD | 單筆有效交易門檻，小於此金額不值得多分散一檔標的。 |
| $N_{\text{core}}$ | $[2, 7]$ 檔 | 核心大盤配置檔數，由資本容量求解器動態計算。 |
| $N_{\text{satellite}}$ | $[0, 16]$ 檔 | 動能衛星配置檔數，由資本容量求解器動態計算。 |
| $\sigma_{252}$ | 百分比 (%) | 標的近 252 個交易日滾動年化實現波動度（個人持倉移動停利求解專用）。 |
| $T_i$ | $[10\%, 30\%]$ | 標的 $i$ 自適應動態停利報酬率門檻 ($T_i = \text{Clamp}(0.75 \times \sigma_{252, i}, 10\%, 30\%)$)。 |
| $D_i$ | $[5.0\%, 12.0\%]$ | 標的 $i$ 自適應動態高點回撤門檻 ($D_i = \text{Clamp}(0.30 \times \sigma_{252, i}, 5\%, 12\%)$)。 |
| $\text{XIRR}_{\text{hurdle}}$ | 10.0% | 標普 500 長線年化基準門檻，停利必須超越此基準方可收割純 Alpha。 |
| $\theta_{\text{drift}}$ | 25.0% | 權重漂移容忍區間，實際權重偏離目標達 $\pm 25\%$ 時觸發再平衡工單。 |
| $\text{MEAT}$ | 30,000 TWD | 最小有效獲利金額約束 (Minimum Effective Action Threshold)，反推自月定額 1 萬、10 個月本金 10 萬大波段爆發，抑制瑣碎工單。 |
| $1.4N$ | 乘數 1.4 | 半年度換倉安全緩衝倍率 (Buffer Zone Multiplier)。 |
| $\text{AUM}$ | TWD | 最新動態基金總資產管理規模 ($\text{發行單位數} \times \text{最新 NAV}$)。 |
| $\rho_{\text{core}}$ | 數值 $[-1, 1]$ | 標的與基準指數 365 日曆天原始日報酬之皮爾森相關係數。 |
| $76\text{W}$ | 稅務標籤 | 台灣稅法「海外利息所得」代碼（海外債券型 ETF 配息專屬免稅標籤）。 |


