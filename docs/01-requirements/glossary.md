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

### 2.1 系統全域層實體 (Global Entities)

| 實體 / 物件 | 英文代碼 | 定義 | 關鍵屬性 |
| --- | --- | --- | --- |
| **市場行情快照** | `MarketDailyQuote` | 單一 ETF 在特定交易日的市場價量。 | `ticker`, `trade_date`, `close_price`, `open_price`, `high_price`, `low_price`, `volume` |
| **宏觀殖利率快照** | `MacroYieldSnapshot` | FRED API 定時拉取之美國公司債與公債殖利率。 | `record_date`, `us_ig_corp_ytm`, `us_treasury_10y`, `us_treasury_20y`, `state` |
| **全域標的評分記錄** | `GlobalAssetScore` | 模組 G-02 每半年對 ETF 進行客觀多因子計算之分數與排名。 | `ticker`, `evaluation_date`, `asset_class`, `total_score`, `global_rank`, `ter`, `aum_twd` |
| **定期定額熱門排行** | `DcaPopularityRank` | 臺灣證交所每月公告之定期定額交易戶數排行。 | `ticker`, `ranking_month`, `rank_position`, `account_count` |
| **全域標的元資料** | `GlobalAssetMetadata` | 標的基本檔案資料，由 `listing_date` 動態推算掛牌天數，免獨立新標的表。 | `ticker`, `name`, `listing_date`, `underlying_index`, `issuer`, `ter`, `aum_twd` |
| **除息公告資訊** | `DividendAnnouncement` | 發行投信公開公告之 ETF 每期除權息日程。 | `ticker`, `ex_date` (除息日), `payment_date` (發放日), `dividend_per_share` |

### 2.2 個人投組層實體 (Personal Entities)

| 實體 / 物件 | 英文代碼 | 定義 | 關鍵屬性 |
| --- | --- | --- | --- |
| **個人持倉** | `PersonalPosition` | 使用者實際持有的單一標的現況（極簡模型）。 | `user_id`, `ticker`, `asset_class`, `total_shares`, `avg_cost_price`, `current_price` |
| **定期定額扣款排程** | `DcaSchedule` | 單一標的之獨立自動扣款設定。 | `user_id`, `ticker`, `dca_days` (扣款日陣列), `dca_amount` (約定扣款額), `dca_status` |
| **再平衡執行工單** | `RebalanceWorkOrder` | 工單求解器產出之單筆整股與盤中零股交易指令。 | `order_id`, `user_id`, `ticker`, `action` (BUY/SELL), `round_lots` (張), `odd_shares` (股), `priority` |
| **交易成交流水帳** | `TradeTransaction` | 使用者確認成交之買賣記錄（記錄已實現資本利得）。 | `tx_id`, `user_id`, `ticker`, `action`, `shares`, `price`, `fees`, `tax`, `realized_gain` |
| **已實現配息收益記錄** | `DividendTransaction` | 除息發放日自動結算入帳之配息流水帳。 | `div_id`, `user_id`, `ticker`, `payment_date`, `shares_held`, `dividend_per_share`, `total_dividend`, `tax_tag` |
| **資產淨值月快照** | `MonthlyPortfolioSnapshot` | 每月總資產淨值與增減變動記錄（取代 Excel）。 | `snapshot_month`, `total_net_worth`, `equity_value`, `bond_value`, `free_cash`, `mom_change_amount`, `mom_change_pct` |

---

## 3. 領域列舉值與狀態機 (Domain Enums & State Machines)

### 3.1 宏觀利率狀態 (`MacroState`)
| 列舉值 | 英文代碼 | 判定條件 | 系統處置行為 |
| --- | --- | --- | --- |
| **高利蓄水期** | `STATE_1_ACCUMULATE` | 美國投資級公司債 YTM $> 5.0\%$ | 債券維持積極定額扣款；衛星利潤 30% 注水債券；股債目標比 80% : 20%。 |
| **常態平衡期** | `STATE_2_NEUTRAL` | $4.0\% \le \text{YTM} \le 5.0\%$ | 債券停止續扣；債息全額回填股票；股債目標比 85% : 15%。 |
| **低利收割期** | `STATE_3_HARVEST` | 美國投資級公司債 YTM $< 3.5\%$ | 債券觸發停利不續扣；分批出清債券賺取價差；100% 抄底核心股票；股債目標比 95% : 5%。 |

### 3.2 資產層級與標的狀態 (`PositionAssetClass`)
| 列舉值 | 英文代碼 | 業務定義 |
| --- | --- | --- |
| **核心大盤** | `CORE` | 長期資產基石，穩健 Beta 增長（如 0050、006208、00646），永久豁免主動出清。 |
| **動能衛星** | `SATELLITE` | 趨勢動能標的，提供夏農波動收割超額利潤。 |
| **防禦債券** | `DEFENSIVE` | 現券型投資級公司債 ETF（如 00720B），鎖定高息防禦墊。 |
| **孤兒標的** | `ORPHAN` | 跌出半年度 1.4N 緩衝區外之舊標的，系統停止扣款並排定優先出清。 |

### 3.3 扣款排程狀態 (`DcaScheduleStatus`)
| 列舉值 | 英文代碼 | 業務定義 |
| --- | --- | --- |
| **正常扣款中** | `ACTIVE` | 每月依約定扣款日由券商自動扣款。 |
| **暫停扣款** | `PAUSED` | 系統自動暫停扣款（如債券進入低利收割期、或標的被標記為 ORPHAN 時）。 |

### 3.4 工單執行優先序 (`OrderPriority`)
| 列舉值 | 英文代碼 | 優先序等級 | 業務說明 |
| --- | --- | --- | --- |
| **第一優先（出清/停利賣單）** | `SELL_FIRST` | 1 | 優先出清孤兒標的與衛星停利賣單，先釋出交割款。 |
| **第二優先（核心回填買單）** | `BUY_CORE` | 2 | 確保有足夠現金後，執行核心大盤補缺買單。 |
| **第三優先（債券蓄水買單）** | `BUY_DEFENSIVE` | 3 | 高利蓄水期之防禦債券單筆買單。 |

### 3.5 退休航道偏離狀態 (`TrajectoryState`)
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
| $C_{\text{monthly}}$ | 變數 (TWD) | 使用者每月新增儲蓄投入金額。 |
| $C_{\text{min\_lot}}$ | 3,000 TWD | 單筆有效交易門檻，小於此金額不值得多分散一檔標的。 |
| $N_{\text{core}}$ | $[2, 7]$ 檔 | 核心大盤配置檔數，由資本容量求解器動態計算。 |
| $N_{\text{satellite}}$ | $[0, 16]$ 檔 | 動能衛星配置檔數，由資本容量求解器動態計算。 |
| $\sigma_{252}$ | 百分比 (%) | 標的近 252 個交易日滾動年化實現波動度。 |
| $T_i$ | $[10\%, 30\%]$ | 標的 $i$ 自適應動態停利報酬率門檻 ($T_i = \text{Clamp}(0.75 \times \sigma_{252, i}, 10\%, 30\%)$)。 |
| $\text{Trailing DD}$ | 8.0% | 移動停利自高點回撤門檻，達標後回撤達 8% 始下達賣單。 |
| $\theta_{\text{drift}}$ | 25.0% | 權重漂移容忍區間，實際權重偏離目標達 $\pm 25\%$ 時觸發再平衡工單。 |
| $\text{MEAT}$ | 20,000 TWD | 最小有效交易金額約束 (Minimum Effective Action Threshold)，低於此金額抑制工單。 |
| $1.4N$ | 乘數 1.4 | 半年度換倉安全緩衝倍率 (Buffer Zone Multiplier)。 |
| $\text{TER}$ | 百分比 (%) | 基金總內扣費用率 (Total Expense Ratio)。 |
| $\text{AUM}$ | TWD | 基金總資產管理規模 (Assets Under Management)。 |
| $\rho_{\text{core}}$ | 數值 $[-1, 1]$ | 動能衛星與核心大盤 252 日還原總報酬之皮爾森相關係數。 |
| $76\text{W}$ | 稅務標籤 | 台灣稅法「海外利息所得」代碼（海外債券型 ETF 配息專屬免稅標籤）。 |

