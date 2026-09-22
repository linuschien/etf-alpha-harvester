# US-P01 模組：個人資本容量與標的治理 (Personal Capacity & Universe Sizing)

## 背景 (Background)
本模組為 AlphaHarvester 個人投組層（Personal Services）之資產適配核心。市場上常見的投資錯誤包括小額資金過度分散導致昂貴的手續費低消耗損，或是大額資金過度集中於單一標的導致非系統性風險。本模組根據使用者個人設定之每月新增投入資金，動態求解出個人最適的核心與衛星檔數，並將全域客觀排名 (G-02) 結合個人實際持倉與 1.4N 緩衝區機制，自動算出專屬於該投資人的留存標的、推薦新標的與孤兒標的 (ORPHAN)。

---

## US-P01-01：個人資本規模自適應檔數求解

**身份**：投資人 / 投組配置器 (Investor / Portfolio Allocator)

> **As a** 投資人，  
> **I want to** 輸入我的每月新增投入金額 ($C_{\text{monthly}}$)，由系統自動求解最佳的核心與衛星持有檔數，  
> **So that** 我不需要主觀猜測該買幾檔標的，並在交易手續費耗損與非系統性風險分散之間取得完美平衡。

### 驗收條件 (Acceptance Criteria)
- **AC1 (輸入參數驗證)**：
  - 使用者在個人設定介面輸入 $C_{\text{monthly}}$（預設為 50,000 TWD，有效輸入範圍：$3,000 \sim 10,000,000$ TWD）。
  - 單筆有效交易門檻 $C_{\text{min\_lot}}$ 固定為 3,000 TWD（符合台股零股最低手續費效益）。
- **AC2 (容量求解數學公式)**：
  - 股票預設比例 $R_{\text{equity}} = 0.80$，核心股票預設佔比 $w_{\text{core\_ratio}} = 0.65$。
  - 核心檔數：
    $$N_{\text{core}} = \text{Clamp}\left( \left\lfloor \frac{C_{\text{monthly}} \times R_{\text{equity}} \times w_{\text{core\_ratio}}}{C_{\text{min\_lot}}} \right\rfloor, \; 2, \; 7 \right)$$
  - 衛星檔數：
    $$N_{\text{satellite}} = \text{Clamp}\left( \left\lfloor \frac{C_{\text{monthly}} \times R_{\text{equity}} \times (1 - w_{\text{core\_ratio}})}{C_{\text{min\_lot}}} \right\rfloor, \; 0, \; 16 \right)$$
- **AC3 (階梯對照邊界驗收)**：
  - 若 $C_{\text{monthly}} \le 15,000$ TWD：系統嚴格鎖定 $N_{\text{core}} = 2$，$N_{\text{satellite}} = 0 \sim 2$（總監控檔數 $2 \sim 4$ 檔，小資防護）。
  - 若 $C_{\text{monthly}} = 40,000$ TWD：系統求解 $N_{\text{core}} = 4$，$N_{\text{satellite}} = 4$（總檔數 8 檔）。
  - 若 $C_{\text{monthly}} \ge 150,000$ TWD：系統達到上限 $N_{\text{core}} = 7$，$N_{\text{satellite}} = 16$（滿載運作 23 檔波動收割池）。
- **AC4 (介面反饋)**：輸入金額後，介面即時動態呈現建議之階梯定位 (Tier 1~4) 與檔數結構卡片。

---

## US-P01-02：1.4N 個人持倉安全緩衝換倉判定

**身份**：個人策略引擎 (Personal Strategy Engine)

> **As a** 個人策略引擎，  
> **I want to** 在每半年審核時將全域客觀多因子排名 (G-02) 映射至使用者的個人持倉，並套用 1.4N 緩衝區規則，  
> **So that** 避免處於排名邊界的持有標的因短期微小雜訊頻繁進出而白白產生交易稅與手續費摩擦。

### 驗收條件 (Acceptance Criteria)
- **AC1 (三級區間判定規則)**：
  設個人求解之衛星檔數為 $N_{\text{sat}}$，緩衝上限為 $\lfloor 1.4 \times N_{\text{sat}} \rfloor$：
  1. **晉升區間 (Promotion Zone, Rank $1 \sim N_{\text{sat}}$)**：
     - 若全域排名進入此區間且個人尚未持有，標記為「候選納入標的 (Candidate New Entry)」。
  2. **安全緩衝區 (Safety Buffer Zone, Rank $N_{\text{sat}}+1 \sim \lfloor 1.4 \times N_{\text{sat}} \rfloor$)**：
     - 若個人**已持有**該標的，只要其全域排名維持在此區間之內，**系統一律判定為「保留不更動 (Keep)」**，嚴禁發出任何換倉賣單。
  3. **淘汰區間 (Drop-out Zone, Rank $> \lfloor 1.4 \times N_{\text{sat}} \rfloor$)**：
     - 若個人已持有該標的，但全域排名落入此區間，系統判定為「觸發淘汰 (De-listed)」。
- **AC2 (實例運算驗證)**：
  - 設 $N_{\text{sat}} = 10$，則緩衝上限為 $10 \times 1.4 = 14$ 名。
  - 若持有的 00881 全域評分由第 8 名降至第 13 名：落在 $1 \sim 14$ 名安全緩衝區內，系統判定 **續留**。
  - 若持有的 00830 評分降至第 15 名：跌破 14 名，系統判定 **淘汰**。
- **AC3 (審核結果視覺化)**：於每半年審核專區條列「續留清單」、「建議新購標的」與「淘汰清單」，附帶因子分數變動對比。

---

## US-P01-03：個人 ORPHAN 孤兒標的標記與自動處置

**身份**：個人帳本維護器 (Personal Ledger Maintainer)

> **As a** 個人帳本維護器，  
> **I want to** 將被淘汰的持有標的自動標記為 `ORPHAN`（孤兒標的）並停止扣款排程，  
> **So that** 系統不再繼續注資劣質標的，並在下期再平衡工單中排定優先出清以釋出資金。

### 驗收條件 (Acceptance Criteria)
- **AC1 (狀態變更與排程暫停)**：當標的被判定淘汰時，其在庫存資料表中的 `asset_class` 立即由 `SATELLITE` 更新為 `ORPHAN`，同時其對應的定期定額排程狀態 (`dca_status`) 自動變更為 `PAUSED`（停止後續扣款）。
- **AC2 (出清工單關聯)**：在模組 P-02 的工單求解器中，標記為 `ORPHAN` 的標的具有最高優先序 (`SELL_FIRST`)，系統自動計算將其持股全額賣出之建議單。
- **AC3 (使用者撤銷與豁免保障)**：若使用者因特殊個人偏好希望手動保留該標的，介面提供「暫免出清」按鈕，點擊後維持現狀但標註「非合規自選」，不計入量化回測歸因。

