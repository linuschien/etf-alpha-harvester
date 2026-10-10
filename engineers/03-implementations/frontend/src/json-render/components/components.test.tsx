import React from 'react';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { JSONUIProvider, createStateStore } from '@json-render/react';
import { describe, it, expect, vi } from 'vitest';
import AlertDialog from './AlertDialog';
import Breadcrumb from './Breadcrumb';
import MetricCard from './MetricCard';
import ChartComponent from './ChartComponent';
import ChartPlaceholder from './ChartPlaceholder';
import DataTable from './DataTable';
import MonthStepper from './MonthStepper';
import MacroRegimeBanner from './MacroRegimeBanner';
import EventCalendar from './EventCalendar';
import { api } from '@/lib/api-client';

describe('Custom JSON-render Components', () => {
  it('renders and interacts with AlertDialog', async () => {
    const user = userEvent.setup();
    const store = createStateStore({ modals: { 'test-dialog': true } });
    const onConfirm = vi.fn();
    const onCancel = vi.fn();

    render(
      <JSONUIProvider store={store} registry={{}}>
        <AlertDialog
          props={{
            id: 'test-dialog',
            title: '測試確認框',
            description: '確認要執行此操作？',
            onConfirm,
            onCancel,
          }}
        />
      </JSONUIProvider>
    );

    expect(screen.getByText('測試確認框')).toBeInTheDocument();
    expect(screen.getByText('確認要執行此操作？')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /確認/i }));
    expect(onConfirm).toHaveBeenCalled();
    expect(store.get('/modals/test-dialog')).toBe(false);
  });

  it('handles cancel in AlertDialog', async () => {
    const user = userEvent.setup();
    const store = createStateStore({ modals: { 'test-cancel-dialog': true } });
    const onCancel = vi.fn();

    render(
      <JSONUIProvider store={store} registry={{}}>
        <AlertDialog
          props={{
            id: 'test-cancel-dialog',
            title: '取消對話框',
            onCancel,
          }}
        />
      </JSONUIProvider>
    );

    await user.click(screen.getByRole('button', { name: /取消/i }));
    expect(store.get('/modals/test-cancel-dialog')).toBe(false);
  });

  it('renders Breadcrumb correctly', () => {
    render(
      <Breadcrumb
        props={{
          items: [
            { label: '首頁', href: '/' },
            { label: '情報中心', href: '/intelligence' },
            { label: '全球天梯' },
          ],
        }}
      />
    );

    expect(screen.getByText('首頁')).toBeInTheDocument();
    expect(screen.getByText('情報中心')).toBeInTheDocument();
    expect(screen.getByText('全球天梯')).toBeInTheDocument();
  });

  it('renders and handles MetricCard interactions', async () => {
    const user = userEvent.setup();
    const store = createStateStore({ metrics: { 'kpi-score': '95.5' } });
    const onClick = vi.fn();

    render(
      <JSONUIProvider store={store} registry={{}}>
        <MetricCard
          props={{
            id: 'kpi-score',
            label: '綜合評分',
            change: '+2.5%',
            sublabel: '優於 92% 標的',
            onClick,
          }}
        />
      </JSONUIProvider>
    );

    expect(screen.getByText('綜合評分')).toBeInTheDocument();
    expect(screen.getByText('95.5')).toBeInTheDocument();
    expect(screen.getByText('+2.5%')).toBeInTheDocument();
    expect(screen.getByText('優於 92% 標的')).toBeInTheDocument();

    await user.click(screen.getByText('綜合評分'));
    expect(onClick).toHaveBeenCalled();
  });

  it('renders ChartPlaceholder properly', () => {
    const BarChart = ChartPlaceholder('Bar');
    render(
      <BarChart
        props={{
          id: 'test-chart',
          label: '柱狀分析圖',
          data_ref: 'getQuoteData',
        }}
      />
    );

    expect(screen.getByText(/\[Bar\]/i)).toBeInTheDocument();
    expect(screen.getByText('柱狀分析圖')).toBeInTheDocument();
    expect(screen.getByText(/數據來源: getQuoteData/i)).toBeInTheDocument();
  });

  it('tests DataTable sorting and singleton rendering', async () => {
    const user = userEvent.setup();
    const rows = [
      {
        clusterId: 2,
        ticker: '0056',
        name: '元大高股息',
        compositeScore: 84.1,
        isSingleton: true,
      },
      {
        clusterId: 1,
        ticker: '0050',
        name: '元大台灣50',
        compositeScore: 92.4,
        isSingleton: false,
      },
    ];

    render(
      <DataTable
        props={{
          id: 'test-table',
          label: '測試表格',
          columns: [
            { field: 'clusterId', label: '編號', sortable: true },
            { field: 'ticker', label: '代碼', sortable: true },
            { field: 'isSingleton', label: '角色' },
            { field: 'compositeScore', label: '評分', sortable: true },
          ],
          data: rows,
        }}
      />
    );

    expect(screen.getByText('測試表格')).toBeInTheDocument();
    expect(screen.getByText('單兵獨立')).toBeInTheDocument();
    expect(screen.getByText('👑 領頭羊')).toBeInTheDocument();

    // Click to sort by compositeScore
    const scoreHeader = screen.getByText('評分');
    await user.click(scoreHeader);
    expect(scoreHeader).toBeInTheDocument();
  });

  it('tests DataTable orthogonalStatus and distributionFrequency badges and formatting', () => {
    const rows = [
      {
        classRank: 1,
        ticker: '0050',
        orthogonalStatus: 'ACCEPTED',
        distributionFrequency: 'SEMI_ANNUAL',
      },
      {
        classRank: 2,
        ticker: '00713',
        orthogonalStatus: 'ACCEPTED',
        distributionFrequency: 'QUARTERLY',
      },
      {
        classRank: 3,
        ticker: '006208',
        orthogonalStatus: 'REJECTED_COLLINEAR',
        distributionFrequency: 'MONTHLY',
      },
      {
        classRank: 4,
        ticker: '00878',
        orthogonalStatus: 'ACCEPTED',
        distributionFrequency: null,
      },
    ];

    render(
      <DataTable
        props={{
          id: 'status-freq-table',
          columns: [
            { field: 'classRank', label: '排名' },
            { field: 'ticker', label: '代碼' },
            { field: 'orthogonalStatus', label: '正交狀態' },
            { field: 'distributionFrequency', label: '配息頻率' },
          ],
          data: rows,
        }}
      />
    );

    expect(screen.getByText('#1')).toBeInTheDocument();
    expect(screen.getByText('#2')).toBeInTheDocument();
    expect(screen.getByText('#3')).toBeInTheDocument();
    expect(screen.getByText('#4')).toBeInTheDocument();
    expect(screen.getByText('⚓ 錨定種子')).toBeInTheDocument();
    expect(screen.getAllByText('正交合規')).toHaveLength(2);
    expect(screen.getByText('共線剔除')).toBeInTheDocument();
    expect(screen.getByText('半年配')).toBeInTheDocument();
    expect(screen.getByText('季配')).toBeInTheDocument();
    expect(screen.getByText('月配')).toBeInTheDocument();
    expect(screen.getByText('--')).toBeInTheDocument();
    expect(screen.queryByText('操作')).not.toBeInTheDocument();
  });

  it('switches anchor seed and stops propagation when clicking orthogonalStatus badge', async () => {
    const user = userEvent.setup();
    const store = createStateStore({ filters: {} });
    const rows = [
      { classRank: 1, ticker: '0050', orthogonalStatus: 'ACCEPTED' },
      { classRank: 2, ticker: '00713', orthogonalStatus: 'ACCEPTED' },
    ];
    let rowClicked = false;
    render(
      <JSONUIProvider store={store} registry={{}}>
        <DataTable
          props={{
            id: 'anchor-test-table',
            columns: [
              { field: 'ticker', label: '代碼' },
              { field: 'orthogonalStatus', label: '正交狀態' },
            ],
            data: rows,
            onRowClick: () => {
              rowClicked = true;
            },
          }}
        />
      </JSONUIProvider>
    );

    const badge00713 = screen.getByText('正交合規');
    await user.click(badge00713);

    // Anchor updated in store
    expect(store.get('/filters/seedTicker')).toBe('00713');
    // Propagation stopped: onRowClick not triggered
    expect(rowClicked).toBe(false);
  });

  it('truncates long ETF name and sets title attribute', () => {
    const longName = '元大美國政府20年期(以上)債券ETF傘型證券投資信託基金之元大美國政府20年期(以上)債券證券投資信託基金';
    render(
      <DataTable
        props={{
          id: 'long-name-table',
          columns: [
            { field: 'ticker', label: '代碼' },
            { field: 'name', label: '名稱' },
          ],
          data: [{ ticker: '00679B', name: longName }],
        }}
      />
    );
    const el = screen.getByTitle(longName);
    expect(el).toBeInTheDocument();
    expect(el).toHaveClass('truncate');
  });

  it('formats return rates and changePct with +/- signs, %, and red/green colors', () => {
    const rows = [
      {
        ticker: '0050',
        changePct: 1.25,
        return1m: 3.2,
        return3m: -2.15,
        return6m: 0,
        return1y: 35.4,
      },
    ];

    render(
      <DataTable
        props={{
          id: 'return-test-table',
          columns: [
            { field: 'ticker', label: '代碼' },
            { field: 'changePct', label: '單日漲跌' },
            { field: 'return1m', label: '1個月' },
            { field: 'return3m', label: '3個月' },
            { field: 'return6m', label: '6個月' },
            { field: 'return1y', label: '1年' },
          ],
          data: rows,
        }}
      />
    );

    const posChange = screen.getByText('+1.25%');
    expect(posChange).toBeInTheDocument();
    expect(posChange).toHaveClass('text-emerald-600');

    const posReturn1m = screen.getByText('+3.20%');
    expect(posReturn1m).toBeInTheDocument();
    expect(posReturn1m).toHaveClass('text-emerald-600');

    const negReturn3m = screen.getByText('-2.15%');
    expect(negReturn3m).toBeInTheDocument();
    expect(negReturn3m).toHaveClass('text-rose-600');

    const zeroReturn6m = screen.getByText('0.00%');
    expect(zeroReturn6m).toBeInTheDocument();
    expect(zeroReturn6m).toHaveClass('text-muted-foreground');

    const posReturn1y = screen.getByText('+35.40%');
    expect(posReturn1y).toBeInTheDocument();
    expect(posReturn1y).toHaveClass('text-emerald-600');
  });

  it('renders MonthStepper and operates month stepping', async () => {
    const user = userEvent.setup();
    const store = createStateStore({
      filters: {
        'test-month': '2026-05-01',
      },
    });

    render(
      <JSONUIProvider store={store} registry={{}}>
        <MonthStepper
          props={{
            label: '評估月份',
            value: { $bindState: '/filters/test-month' },
          }}
        />
      </JSONUIProvider>
    );

    expect(screen.getByText('評估月份：')).toBeInTheDocument();
    expect(screen.getByText(/2026\s*年\s*5\s*月/)).toBeInTheDocument();

    const prevBtn = screen.getByRole('button', { name: '上個月' });
    await user.click(prevBtn);
    expect(store.get('/filters/test-month')).toBe('2026-04-01');
    expect(screen.getByText(/2026\s*年\s*4\s*月/)).toBeInTheDocument();

    const nextBtn = screen.getByRole('button', { name: '下個月' });
    await user.click(nextBtn);
    expect(store.get('/filters/test-month')).toBe('2026-05-01');
  });

  it('renders ChartComponent with panic sparkline channel thresholds and status', () => {
    const store = createStateStore({
      data: {
        quoteTimeSeries: {
          fearGreed: [
            { tradeDate: '2026-09-01', closePrice: 85 },
            { tradeDate: '2026-09-02', closePrice: 20 },
          ],
          move: [
            { tradeDate: '2026-09-01', closePrice: 50 },
            { tradeDate: '2026-09-02', closePrice: 130 },
          ],
        },
      },
    });

    render(
      <JSONUIProvider store={store} registry={{}}>
        <ChartComponent props={{ id: 'chart-fear-greed-spark' }} />
        <ChartComponent props={{ id: 'chart-move-spark' }} />
      </JSONUIProvider>
    );

    expect(screen.getByText('FEAR_GREED 恐懼貪婪指數 (CNN)')).toBeInTheDocument();
    expect(screen.getByText('^MOVE 美債波動率指數 (美債期權)')).toBeInTheDocument();
    expect(screen.getAllByText(/⚠️ 過度恐慌/i)).toHaveLength(2);
  });

  it('renders MacroRegimeBanner with HIGH_YIELD_ACCUMULATION and CRISIS_LEVEL_1', () => {
    const store = createStateStore({
      data: {
        getMacroRegime: {
          macroState: 'HIGH_YIELD_ACCUMULATION',
          recommendedEquityRatio: 0.8,
          recommendedBondRatio: 0.2,
          assessmentSummary:
            '目前處於【高利蓄水期】（公司債有效殖利率 5.97% > 5.0%），建議積極配置防禦債券蓄水，股債比率 80%:20%。',
          crisisLevel: 'CRISIS_LEVEL_1',
          effectiveDate: '2026-09-30',
        },
      },
    });

    render(
      <JSONUIProvider store={store} registry={{}}>
        <MacroRegimeBanner props={{ id: 'macro-regime-banner' }} />
      </JSONUIProvider>
    );

    expect(screen.getByText('宏觀景氣循環與配置策略')).toBeInTheDocument();
    expect(screen.getByText('高利蓄水期 (HIGH_YIELD_ACCUMULATION)')).toBeInTheDocument();
    expect(screen.getByText('80%')).toBeInTheDocument();
    expect(screen.getByText('20%')).toBeInTheDocument();
    expect(screen.getByText(/🚨 恐慌抄底 \(CRISIS_LEVEL_1\)/i)).toBeInTheDocument();
    expect(screen.getByText(/評估基準日: 2026-09-30/i)).toBeInTheDocument();
    expect(
      screen.getByText(/目前處於【高利蓄水期】（公司債有效殖利率 5.97% > 5.0%）/i)
    ).toBeInTheDocument();
  });

  it('renders MacroRegimeBanner with NORMAL_BALANCED, LOW_YIELD_HARVEST, and CRISIS_LEVEL_2', () => {
    const store1 = createStateStore({
      data: {
        getMacroRegime: {
          macroState: 'NORMAL_BALANCED',
          recommendedEquityRatio: 85,
          recommendedBondRatio: 15,
          assessmentSummary: '常態平衡期測試摘要',
          crisisLevel: 'CORRECTION',
        },
      },
      data_snapshot: {
        recordDate: '2026-10-01',
      },
    });

    const { rerender } = render(
      <JSONUIProvider store={store1} registry={{}}>
        <MacroRegimeBanner />
      </JSONUIProvider>
    );

    expect(screen.getByText('常態平衡期 (NORMAL_BALANCED)')).toBeInTheDocument();
    expect(screen.getByText(/⚠️ 修正期警示 \(CORRECTION\)/i)).toBeInTheDocument();
    expect(screen.getByText('85%')).toBeInTheDocument();
    expect(screen.getByText('15%')).toBeInTheDocument();

    // Rerender with LOW_YIELD_HARVEST & CRISIS_LEVEL_2
    const store2 = createStateStore({
      data: {
        getMacroRegime: {
          macroState: 'LOW_YIELD_HARVEST',
          recommendedEquityRatio: 0.95,
          assessmentSummary: '低利收割期測試摘要',
          crisisLevel: 'CRISIS_LEVEL_2',
        },
      },
    });

    rerender(
      <JSONUIProvider store={store2} registry={{}}>
        <MacroRegimeBanner />
      </JSONUIProvider>
    );

    expect(screen.getByText('低利收割期 (LOW_YIELD_HARVEST)')).toBeInTheDocument();
    expect(screen.getByText(/🔥 黑天鵝救災 \(CRISIS_LEVEL_2\)/i)).toBeInTheDocument();
    expect(screen.getByText('95%')).toBeInTheDocument();
    expect(screen.getByText('5%')).toBeInTheDocument();
  });

  it('renders ChartComponent macro-yield-chart with time window filtering and decimated X-axis date labels', async () => {
    // Generate 60 daily records from 2026-08-01 to 2026-09-30
    const sampleHistory = Array.from({ length: 60 }, (_, i) => {
      const day = i + 1;
      const month = day <= 31 ? '08' : '09';
      const dateNum = day <= 31 ? day : day - 31;
      const dateStr = `2026-${month}-${String(dateNum).padStart(2, '0')}`;
      return {
        recordDate: dateStr,
        us10YearTreasuryYield: 3.8 + (i % 5) * 0.1,
        us20YearTreasuryYield: 4.1 + (i % 4) * 0.1,
        usCorporateBondEffectiveYield: 5.2 + (i % 3) * 0.1,
        yieldSpread10yMinus2y: 0.2 + (i % 4) * 0.05,
      };
    });

    const store = createStateStore({
      data: {
        listMacroYieldSnapshots: sampleHistory,
        getLatestMacroYieldSnapshot: sampleHistory[sampleHistory.length - 1],
      },
      filters: {
        'macro-yield-window-selector': '1M',
      },
    });

    const { rerender } = render(
      <JSONUIProvider store={store} registry={{}}>
        <ChartComponent props={{ id: 'macro-yield-chart', label: '利率走勢圖' }} />
      </JSONUIProvider>
    );

    // Verify 1M window badge
    expect(screen.getByText(/週期: 1M/i)).toBeInTheDocument();

    // Verify X-axis ticks: should NOT be 60 labels, but at most 5 unique ticks!
    const ticks1M = screen.getAllByText(/^2026\/\d{2}\/\d{2}$/);
    expect(ticks1M.length).toBeLessThanOrEqual(5);
    expect(ticks1M.length).toBeGreaterThanOrEqual(2);

    // Switch window to 1Y
    store.set('/filters/macro-yield-window-selector', '1Y');
    rerender(
      <JSONUIProvider store={store} registry={{}}>
        <ChartComponent props={{ id: 'macro-yield-chart', label: '利率走勢圖' }} />
      </JSONUIProvider>
    );

    expect(screen.getByText(/週期: 1Y/i)).toBeInTheDocument();
    const ticks1Y = screen.getAllByText(/^2026\/\d{2}\/\d{2}$/);
    expect(ticks1Y.length).toBeLessThanOrEqual(5);
  });

  it('renders ChartComponent drawdown-radar-chart and verifies TradingView-style crosshair axis badges on hover', () => {
    // Mock SVG methods in JSDOM
    SVGSVGElement.prototype.createSVGPoint = () =>
      ({
        x: 0,
        y: 0,
        matrixTransform: () => ({ x: 300, y: 150 }),
      } as any);
    SVGSVGElement.prototype.getScreenCTM = () =>
      ({
        inverse: () => ({} as any),
      } as any);

    const sampleQuotes = [
      { tradeDate: '2026-09-01', closePrice: 20000, ma20: 19800, ma60: 19500, ma120: 19000, ma240: 18500, bbUpper: 20500, bbLower: 19500 },
      { tradeDate: '2026-09-02', closePrice: 20500, ma20: 19900, ma60: 19550, ma120: 19050, ma240: 18550, bbUpper: 20800, bbLower: 19600 },
      { tradeDate: '2026-09-03', closePrice: 21000, ma20: 20100, ma60: 19600, ma120: 19100, ma240: 18600, bbUpper: 21200, bbLower: 19800 },
    ];

    const store = createStateStore({
      data: {
        quoteTimeSeries: {
          twii: sampleQuotes,
        },
      },
      filters: {
        'drawdown-benchmark-selector': '^TWII',
        'drawdown-window-selector': '6M',
      },
    });

    const { container } = render(
      <JSONUIProvider store={store} registry={{}}>
        <ChartComponent props={{ id: 'drawdown-radar-chart', label: '52 週高點回撤走勢圖' }} />
      </JSONUIProvider>
    );

    expect(screen.getByText(/台股加權指數 \(\^TWII\) 52 週回撤雷達/)).toBeInTheDocument();
    expect(screen.getByText(/最新指數點數/)).toBeInTheDocument();

    const svg = container.querySelector('svg.cursor-crosshair');
    expect(svg).toBeInTheDocument();

    // Trigger hover
    fireEvent.mouseMove(svg!, { clientX: 300, clientY: 150 });

    // Live inspection badge in header
    expect(screen.getByText(/游標點數:/)).toBeInTheDocument();

    // Mouse leave
    fireEvent.mouseLeave(svg!);
    expect(screen.queryByText(/游標點數:/)).not.toBeInTheDocument();
  });

  it('renders PairwiseMatrixHeatmap empty state when no data exists', () => {
    const store = createStateStore({
      data: {
        listPairwiseMatrix: [],
      },
    });

    render(
      <JSONUIProvider store={store} registry={{}}>
        <ChartComponent props={{ id: 'pairwise-matrix-chart', label: '相關性判定熱圖' }} />
      </JSONUIProvider>
    );

    expect(screen.getByText('尚無兩兩正交檢驗矩陣資料')).toBeInTheDocument();
  });

  it('renders PairwiseMatrixHeatmap with interactive scope switching, search highlighting, and cell inspection', async () => {
    // Generate 25 tickers to test Top 10, Top 20, and All
    const tickers = Array.from({ length: 25 }, (_, i) => `00${50 + i}`);
    const matrixData: any[] = [];
    for (let i = 0; i < tickers.length; i++) {
      for (let j = i + 1; j < tickers.length; j++) {
        const isCollinear = (i + j) % 3 === 0;
        matrixData.push({
          baseTicker: tickers[i],
          targetTicker: tickers[j],
          rSquared: isCollinear ? 0.65 : 0.25,
          correlationCoefficient: isCollinear ? 0.81 : 0.50,
        });
      }
    }

    const store = createStateStore({
      data: {
        listPairwiseMatrix: matrixData,
      },
    });

    render(
      <JSONUIProvider store={store} registry={{}}>
        <ChartComponent props={{ id: 'pairwise-matrix-chart', label: '相關性判定熱圖' }} />
      </JSONUIProvider>
    );

    // Initial state: default to Top 10
    expect(screen.getByText('10 × 10 維度')).toBeInTheDocument();
    expect(screen.getByText('前 10 檔 (Top 10)')).toBeInTheDocument();
    expect(screen.getByText('前 20 檔 (Top 20)')).toBeInTheDocument();
    expect(screen.getByText('全部標的 (25 檔)')).toBeInTheDocument();

    // Switch to Top 20
    const top20Btn = screen.getByRole('button', { name: '前 20 檔 (Top 20)' });
    fireEvent.click(top20Btn);
    expect(screen.getByText('20 × 20 維度')).toBeInTheDocument();

    // Switch to All
    const allBtn = screen.getByRole('button', { name: '全部標的 (25 檔)' });
    fireEvent.click(allBtn);
    expect(screen.getByText('25 × 25 維度')).toBeInTheDocument();

    // Search for a ticker
    const searchInput = screen.getByPlaceholderText('搜尋標的代碼 (如 0052)...');
    fireEvent.change(searchInput, { target: { value: '0052' } });
    expect(screen.getByText('✕ 清除')).toBeInTheDocument();

    // Clear search
    fireEvent.click(screen.getByText('✕ 清除'));
    expect(searchInput).toHaveValue('');

    // Hover inspection
    const cells = screen.getAllByRole('cell');
    // Find a non-header cell with text
    const sampleCell = cells.find((c) => c.textContent?.includes('0.'));
    expect(sampleCell).toBeDefined();

    if (sampleCell) {
      fireEvent.mouseEnter(sampleCell);
      expect(screen.getByText(/判定係數 R²:/)).toBeInTheDocument();

      fireEvent.mouseLeave(sampleCell);
      expect(screen.getByText(/滑鼠懸停於任一交叉格/)).toBeInTheDocument();
    }
  });

  it('renders and interacts with refactored EventCalendar in calendar and table modes', async () => {
    const mockDividends = [
      {
        ticker: '0056',
        exDate: '2026-10-18T00:00:00',
        paymentDate: '2026-11-12T00:00:00',
        dividendPerShare: 1.07,
        taxTag: 'DOMESTIC_54C',
      },
      {
        ticker: '0050',
        exDate: '2026-10-18T00:00:00',
        paymentDate: '2026-11-15T00:00:00',
        dividendPerShare: 0.60,
        taxTag: 'DOMESTIC_54C',
      },
    ];

    const mockSplits = [
      {
        ticker: '0050',
        effectiveDate: '2026-10-18T00:00:00',
        splitToShares: 4,
        splitFromShares: 1,
      },
    ];

    const store = createStateStore({
      data: {
        listDividendAnnouncements: mockDividends,
        listCorporateActions: mockSplits,
      },
    });

    render(
      <JSONUIProvider store={store} registry={{}}>
        <EventCalendar />
      </JSONUIProvider>
    );

    // 1. Calendar Mode (Default)
    expect(screen.getByText(/ETF 除息月曆與股票分割事件視圖/)).toBeInTheDocument();
    expect(screen.getByText('2026 年 10 月')).toBeInTheDocument();
    expect(screen.getByText('當月 (10月)')).toBeInTheDocument();

    // Verify frequency dropdown is removed
    expect(screen.queryByText('全部配息週期')).not.toBeInTheDocument();

    // Verify Split priority on cell badge (🟣 分割 0050 4:1)
    await waitFor(() => {
      expect(screen.getByText('🟣 分割 0050 4:1')).toBeInTheDocument();
    });

    // Click on 18th to inspect detail card
    const day18Cells = screen.getAllByText('18');
    const day18Cell = day18Cells[0].closest('div');
    if (day18Cell) {
      fireEvent.click(day18Cell);
      expect(screen.getByText(/2026-10-18 事件明細/)).toBeInTheDocument();
      // Should show ticker without duplicate name
      expect(screen.getAllByText('0050').length).toBeGreaterThan(0);
      expect(screen.queryByText('0050 0050')).not.toBeInTheDocument();
    }

    // 2. Switch to Table Mode
    const tableToggle = screen.getByRole('button', { name: '清單' });
    fireEvent.click(tableToggle);

    // Month steppers should be hidden
    expect(screen.queryByText('2026 年 10 月')).not.toBeInTheDocument();

    // Text search input and search button should be visible
    const searchInput = screen.getByPlaceholderText('搜尋標的代碼 (如 0050)...');
    expect(searchInput).toBeInTheDocument();
    const queryBtn = screen.getByRole('button', { name: '查詢' });
    expect(queryBtn).toBeInTheDocument();

    // Default empty state prompt
    expect(screen.getByText('請輸入標的代碼以查詢歷史除息與分割記錄')).toBeInTheDocument();

    // Search for 0056
    fireEvent.change(searchInput, { target: { value: '0056' } });
    fireEvent.click(queryBtn);

    // Wait for paired row results
    await waitFor(() => {
      expect(screen.getByText('0056')).toBeInTheDocument();
      expect(screen.getByText('🟢 2026-10-18')).toBeInTheDocument();
      expect(screen.getByText('🔵 2026-11-12')).toBeInTheDocument();
      expect(screen.getByText('$1.07 TWD')).toBeInTheDocument();
      expect(screen.getByText('54C 境內股利')).toBeInTheDocument();
    });

    // Ensure no redundant columns
    expect(screen.queryByText('標的名稱')).not.toBeInTheDocument();
    expect(screen.queryByText('配息週期')).not.toBeInTheDocument();

    // Search for invalid ticker 9999
    fireEvent.change(searchInput, { target: { value: '9999' } });
    fireEvent.click(queryBtn);

    await waitFor(() => {
      expect(screen.getByText(/查無標的「9999」的除息或分割記錄/)).toBeInTheDocument();
    });

    // Clear search using ✕
    const clearBtn = screen.getByTitle('清除');
    fireEvent.click(clearBtn);
    expect(searchInput).toHaveValue('');
    expect(screen.getByText('請輸入標的代碼以查詢歷史除息與分割記錄')).toBeInTheDocument();
  });
});

