import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { JSONUIProvider, createStateStore } from '@json-render/react';
import { componentRegistry } from '@/json-render/component-registry';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import GlobalMarketIntelligencePage from './global-market-intelligence.page';

const store = createStateStore({
  modals: {},
  form: {},
  data: {},
  filters: {},
  metrics: {},
});

const executeBehavior = vi.fn();
const openModal = vi.fn((p: any) => {
  if (p?.id) store.set(`/modals/${p.id}`, true);
});
const closeModal = vi.fn((p: any) => {
  if (p?.id) store.set(`/modals/${p.id}`, false);
});
const selectTab = vi.fn((p: any) => {
  if (p?.tab) store.set('/activeTab', p.tab);
});
const selectSubTab = vi.fn((p: any) => {
  if (p?.subTab) store.set('/activeSubTab', p.subTab);
});
const selectDcaSubTab = vi.fn((p: any) => {
  if (p?.dcaSubTab) store.set('/activeDcaSubTab', p.dcaSubTab);
});
const navigate = vi.fn();
const testHandlers = {
  navigate,
  openModal,
  closeModal,
  selectTab,
  selectSubTab,
  selectDcaSubTab,
  executeBehavior,
};

function renderPage() {
  const qc = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return render(
    <QueryClientProvider client={qc}>
      <JSONUIProvider
        registry={componentRegistry}
        store={store}
        handlers={testHandlers as any}
      >
        <GlobalMarketIntelligencePage />
      </JSONUIProvider>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  store.set('/modals', {});
  store.set('/form', {});
  store.set('/data', {});
  store.set('/filters', {});
  store.set('/metrics', {});
  store.set('/activeTab', 'all');
  store.set('/activeSubTab', 'all');
  store.set('/activeDcaSubTab', 'all');
  vi.clearAllMocks();
});

describe('GlobalMarketIntelligencePage', () => {
  // ── Pattern 1 — Render ───────────────────────────────────────────────────
  it('renders page heading and core tabs without Tab 1/2/3 prefix', async () => {
    renderPage();
    expect(
      await screen.findByRole('heading', {
        name: /台股 ETF 量化收割戰情室/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByText(/台股 ETF 量化篩選 · 波動收割 · 景氣動態配置/i)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('tab', { name: /全球指數雷達/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('tab', { name: /ETF排行榜/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('tab', { name: /ETF定期定額排行榜與除權息月曆/i })
    ).toBeInTheDocument();
  });

  it('switches main tabs and updates activeTab in store', async () => {
    const user = userEvent.setup();
    renderPage();

    const tab2 = screen.getByRole('tab', { name: /ETF排行榜/i });
    await user.click(tab2);
    expect(store.get('/activeTab')).toBe('qualified-leaderboard-section');

    const tab3 = screen.getByRole('tab', { name: /ETF定期定額排行榜與除權息月曆/i });
    await user.click(tab3);
    expect(store.get('/activeTab')).toBe('dca-calendar-section');

    const tab1 = screen.getByRole('tab', { name: /全球指數雷達/i });
    await user.click(tab1);
    expect(store.get('/activeTab')).toBe('macro-sentiment-section');
  });

  it('renders 3 sub-tabs under macro sentiment radar and switches sub-tabs', async () => {
    const user = userEvent.setup();
    renderPage();

    // Verify all 3 sub-tab buttons are rendered
    const subTab1 = await screen.findByRole('tab', {
      name: /全球指數行情/i,
    });
    const subTab2 = screen.getByRole('tab', {
      name: /情緒指標/i,
    });
    const subTab3 = screen.getByRole('tab', {
      name: /宏觀指引與債券殖利率/i,
    });

    expect(subTab1).toBeInTheDocument();
    expect(subTab2).toBeInTheDocument();
    expect(subTab3).toBeInTheDocument();

    // Click sub-tab 2 (Four Panic Indicators)
    await user.click(subTab2);
    expect(store.get('/activeSubTab')).toBe('macro-subtab-panic');

    // Click sub-tab 3 (Macro Yield Curve)
    await user.click(subTab3);
    expect(store.get('/activeSubTab')).toBe('macro-subtab-yield');
  });

  it('renders 2 sub-tabs under ETF定期定額排行榜與除權息月曆 and switches sub-tabs', async () => {
    const user = userEvent.setup();
    renderPage();

    // Switch to Tab 3
    const tab3 = screen.getByRole('tab', { name: /ETF定期定額排行榜與除權息月曆/i });
    await user.click(tab3);
    expect(store.get('/activeTab')).toBe('dca-calendar-section');

    // Verify both sub-tab buttons are rendered using exact match to distinguish from main tab
    const dcaSubTab1 = await screen.findByRole('tab', {
      name: /^定期定額排行榜$/,
    });
    const dcaSubTab2 = screen.getByRole('tab', {
      name: /^除權息月曆$/,
    });

    expect(dcaSubTab1).toBeInTheDocument();
    expect(dcaSubTab2).toBeInTheDocument();

    // Click sub-tab 2 (除權息月曆)
    await user.click(dcaSubTab2);
    expect(store.get('/activeDcaSubTab')).toBe('dca-subtab-calendar');

    // Click sub-tab 1 (定期定額排行榜)
    await user.click(dcaSubTab1);
    expect(store.get('/activeDcaSubTab')).toBe('dca-subtab-top20');
  });

  it('renders four panic sentiment indicators in 2x2 matrix with sparklines', async () => {
    renderPage();

    // Verify 4 panic indicators exist
    expect(await screen.findByText(/\^VIX 波動率指數/i)).toBeInTheDocument();
    expect(screen.getByText(/\^VXN 那指波動率指數/i)).toBeInTheDocument();
    expect(screen.getByText(/FEAR_GREED 恐懼貪婪指數/i)).toBeInTheDocument();
    expect(screen.getByText(/\^MOVE 美債波動率指數/i)).toBeInTheDocument();
  });

  it('renders macro yield sub-tab with MacroRegimeBanner and 4 rate cards with trade dates, without redundant title', async () => {
    const user = userEvent.setup();
    renderPage();

    const subTab3 = await screen.findByRole('tab', {
      name: /宏觀指引與債券殖利率/i,
    });
    await user.click(subTab3);
    expect(store.get('/activeSubTab')).toBe('macro-subtab-yield');

    // Verify redundant title is NOT present
    expect(
      screen.queryByText(/宏觀利率與殖利率曲線單一整合走勢圖 \(Macro Yield & Spread\)/i)
    ).not.toBeInTheDocument();

    // Verify MacroRegimeBanner is rendered
    expect(await screen.findByText('宏觀景氣循環與配置策略')).toBeInTheDocument();
    expect(screen.getByText(/建議股票配置/i)).toBeInTheDocument();
    expect(screen.getByText(/建議防禦債券配置/i)).toBeInTheDocument();

    // Verify 4 yield metric cards are rendered
    expect(screen.getByText(/10Y 美國公債殖利率 \(DGS10\)/i)).toBeInTheDocument();
    expect(screen.getByText(/20Y 美國公債殖利率 \(DGS20\)/i)).toBeInTheDocument();
    expect(screen.getByText(/10Y-2Y 殖利率利差 \(T10Y2Y\)/i)).toBeInTheDocument();
    expect(screen.getByText(/ICE BofA 企業債殖利率 \(BAMLC0A0CMEY\)/i)).toBeInTheDocument();

    // Verify dates on cards
    await waitFor(() => {
      const dateElements = screen.getAllByText(/交易日: 2026-09-30/i);
      expect(dateElements.length).toBeGreaterThanOrEqual(4);
    });
  });

  it('switches time window on macro yield chart when clicking window selector buttons', async () => {
    const user = userEvent.setup();
    renderPage();

    const subTab3 = await screen.findByRole('tab', {
      name: /宏觀指引與債券殖利率/i,
    });
    await user.click(subTab3);

    // Initial default window is 6M
    expect(screen.getByText(/週期: 6M/i)).toBeInTheDocument();

    const yieldToolbar = screen.getByText(/時間視窗切換 \(1M \/ 3M \/ 6M \(預設\) \/ 1Y\)/i).closest('div');
    expect(yieldToolbar).not.toBeNull();

    // Click 1M button within yield toolbar
    const btn1M = within(yieldToolbar!).getByRole('button', { name: '1M' });
    await user.click(btn1M);
    expect(store.get('/filters/macro-yield-window-selector')).toBe('1M');
    expect(screen.getByText(/週期: 1M/i)).toBeInTheDocument();

    // Click 1Y button within yield toolbar
    const btn1Y = within(yieldToolbar!).getByRole('button', { name: '1Y' });
    await user.click(btn1Y);
    expect(store.get('/filters/macro-yield-window-selector')).toBe('1Y');
    expect(screen.getByText(/週期: 1Y/i)).toBeInTheDocument();
  });

  // ── Pattern 2 — Query (store-based table data) ───────────────────────────
  it('shows empty state when no store data is present', async () => {
    renderPage();
    const emptyElements = await screen.findAllByText('(沒有資料)');
    expect(emptyElements.length).toBeGreaterThan(0);
  });

  it('renders rows when orthogonal candidates store data is populated', async () => {
    const mockOrthogonal = [
      {
        classRank: 1,
        orthogonalStatus: '錨定種子',
        ticker: '0050',
        name: '元大台灣50',
        distributionFrequency: '半年配',
        closePrice: 198.5,
        changePct: 0.85,
        return1m: 3.2,
        return3m: 8.5,
        return6m: 15.6,
        return1y: 35.4,
        fundSizeTwd: 420000000000,
        compositeScore: 92.4,
      },
    ];

    store.set('/data/getOrthogonalCandidates', mockOrthogonal);
    renderPage();

    expect(await screen.findByText('元大台灣50')).toBeInTheDocument();
    expect(screen.getByText('0050')).toBeInTheDocument();
    expect(screen.getByText('+0.85%')).toBeInTheDocument();
  });

  it('renders hierarchical clustering table and expands alternative rows', async () => {
    const user = userEvent.setup();
    const mockClusters = [
      {
        clusterId: 1,
        isSingleton: false,
        leader: {
          ticker: '0050',
          name: '元大台灣50',
          underlyingIndex: '富時臺灣證券交易所臺灣50指數',
          closePrice: 198.5,
          changePct: 0.85,
          return1m: 3.2,
          return3m: 8.5,
          return6m: 15.6,
          return1y: 35.4,
          fundSizeTwd: 420000000000,
          compositeScore: 92.4,
        },
        alternatives: [
          {
            ticker: '006208',
            name: '富邦台50',
            underlyingIndex: '富時臺灣證券交易所臺灣50指數',
            rSquaredWithLeader: 0.998,
            compositeScoreGap: -1.2,
            closePrice: 115.2,
            changePct: 0.82,
            return1m: 3.1,
            return3m: 8.4,
            return6m: 15.5,
            return1y: 35.2,
            fundSizeTwd: 180000000000,
            compositeScore: 91.2,
          },
        ],
      },
    ];

    store.set('/data/getClusteredCandidates', mockClusters);
    store.set('/filters/perspective-mode-selector', '🧩 分群去冗餘族群');
    renderPage();

    // Leader row should be displayed
    expect(await screen.findByText(/👑 領頭羊 \(1 替代\)/i)).toBeInTheDocument();

    const expandBtn = screen.getByRole('button', { name: /切換折疊/i });
    const clusterTable = expandBtn.closest('table')!;

    // Alternative row should initially not be visible inside the table
    expect(within(clusterTable).queryByText('富邦台50')).not.toBeInTheDocument();

    // Click toggle button to expand cluster
    await user.click(expandBtn);

    // Alternative row should now be visible with R² badge inside the table
    expect(within(clusterTable).getByText('富邦台50')).toBeInTheDocument();
    expect(within(clusterTable).getByText('006208')).toBeInTheDocument();
    expect(within(clusterTable).getByText(/R²: 99.8%/i)).toBeInTheDocument();
  });

  // ── Pattern 3 — Modal Open + User Controls ─────────────────────────────
  it('opens watermark detail modal when data freshness button is clicked', async () => {
    const user = userEvent.setup();
    renderPage();

    const triggerBtn = await screen.findByRole('button', {
      name: /資料同步狀態/i,
    });
    await user.click(triggerBtn);

    expect(openModal).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'watermark-detail-modal' })
    );
    expect(store.get('/modals/watermark-detail-modal')).toBe(true);
  });

  it('renders ETF leaderboard heading and switches candidate asset pool subtabs', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole('heading', { name: /ETF投資清單篩選/i })).toBeInTheDocument();

    const coreTab = screen.getByRole('tab', { name: /核心大盤 \(Core\)/i });
    const satTab = screen.getByRole('tab', { name: /動能衛星 \(Satellite\)/i });
    const defTab = screen.getByRole('tab', { name: /防禦債券 \(Defensive\)/i });

    expect(coreTab).toBeInTheDocument();
    expect(satTab).toBeInTheDocument();
    expect(defTab).toBeInTheDocument();

    await user.click(satTab);
    expect(store.get('/filters/asset-class-selector')).toBe('SATELLITE');
  });

  it('switches evaluation month with month stepper in leaderboard toolbar', async () => {
    const user = userEvent.setup();
    renderPage();

    const now = new Date();
    const curYear = now.getFullYear();
    const curMonth = now.getMonth() + 1;
    let prevMonth = curMonth - 1;
    let prevYear = curYear;
    if (prevMonth < 1) {
      prevMonth = 12;
      prevYear -= 1;
    }
    const expectedPrevDate = `${prevYear}-${String(prevMonth).padStart(2, '0')}-01`;

    expect(
      await screen.findByText(new RegExp(`${curYear}\\s*年\\s*${curMonth}\\s*月\\s*\\(最新\\)`, 'i'))
    ).toBeInTheDocument();

    const prevMonthBtn = screen.getByRole('button', { name: '上個月' });
    await user.click(prevMonthBtn);

    expect(store.get('/filters/leaderboard-evaluation-date')).toBe(expectedPrevDate);
    expect(
      screen.getByText(new RegExp(`${prevYear}\\s*年\\s*${prevMonth}\\s*月`, 'i'))
    ).toBeInTheDocument();
  });

  it('switches perspective mode between Shannon orthogonal and hierarchical clustering tables', async () => {
    const user = userEvent.setup();
    renderPage();

    const clusterRadio = await screen.findByRole('button', {
      name: /🧩 分群去冗餘族群/i,
    });
    await user.click(clusterRadio);
    expect(store.get('/filters/perspective-mode-selector')).toBe('🧩 分群去冗餘族群');

    const shannonRadio = screen.getByRole('button', {
      name: /⚡ 夏農幾何收割/i,
    });
    await user.click(shannonRadio);
    expect(store.get('/filters/perspective-mode-selector')).toBe('⚡ 夏農幾何收割');
  });

  it('auto-closes modal when 關閉 button is clicked', async () => {
    store.set('/modals/watermark-detail-modal', true);
    const user = userEvent.setup();
    renderPage();

    const closeBtn = await screen.findByRole('button', { name: /關閉/i });
    await user.click(closeBtn);

    await waitFor(() => {
      expect(store.get('/modals/watermark-detail-modal')).toBe(false);
    });
  });

  // ── Pattern 4 — Row Actions & Drawer Opening ─────────────────────────────
  it('opens asset detail drawer and updates selected asset on table row click with 3-stage decision modules', async () => {
    const user = userEvent.setup();
    const mockRows = [
      {
        classRank: 1,
        orthogonalStatus: '錨定種子',
        ticker: '0050',
        name: '元大台灣50',
        distributionFrequency: '半年配',
        closePrice: 198.5,
        changePct: 0.85,
        return1m: 3.2,
        return3m: 8.5,
        return6m: 15.6,
        return1y: 35.4,
        fundSizeTwd: 420000000000,
        compositeScore: 92.4,
        rSquared: 0.98,
        sharpeRatio: 1.85,
      },
    ];

    store.set('/data/getOrthogonalCandidates', mockRows);
    renderPage();

    const row = await screen.findByText('元大台灣50');
    await user.click(row);

    expect(store.get('/modals/asset-detail-drawer')).toBe(true);
    expect(store.get('/selectedAsset')).toBe('0050');

    // Verify dynamic drawer heading
    const headings = await screen.findAllByText(/0050 元大台灣50 ｜ 深度決策透視/i);
    expect(headings.length).toBeGreaterThanOrEqual(1);

    // Verify Stage 1: Radar
    expect(screen.getByText(/多因子體質透視雷達/i)).toBeInTheDocument();

    // Verify Stage 2: 52-Week Drawdown Radar
    expect(await screen.findByText(/0050 52 週回撤雷達/i)).toBeInTheDocument();

    // Verify Stage 3: Dip-Buy Opportunity
    expect(await screen.findByText(/超跌加碼綜合評分/i)).toBeInTheDocument();
    expect(await screen.findByText(/78.5 分/i)).toBeInTheDocument();
    expect(screen.getByText(/★★★★ 超跌區/i)).toBeInTheDocument();

    // Verify close drawer button closes modal
    const closeBtn = screen.getByRole('button', { name: /關閉抽屜/i });
    await user.click(closeBtn);
    expect(store.get('/modals/asset-detail-drawer')).toBe(false);
  });

  // ── Pattern 5 — Benchmark Selection & Radar Chart Points ─────────────────
  it('switches active benchmark when clicking benchmark cards', async () => {
    const user = userEvent.setup();
    renderPage();

    // Find the S&P 500 card
    const sp500Card = await screen.findByText(/\^GSPC 標普500指數/i);
    await user.click(sp500Card);

    // Verify store filter is updated to ^GSPC
    expect(store.get('/filters/drawdown-benchmark-selector')).toBe('^GSPC');

    // Find the TWII card and click it back
    const twiiCard = screen.getByText(/\^TWII 台股加權指數/i);
    await user.click(twiiCard);
    expect(store.get('/filters/drawdown-benchmark-selector')).toBe('^TWII');
  });

  it('renders index points and fibonacci labels in the drawdown radar chart', async () => {
    renderPage();

    // Verify index point text in radar chart
    expect(await screen.findByText(/最新指數點數/i)).toBeInTheDocument();
    expect(screen.getByText(/52 週最高點數/i)).toBeInTheDocument();
    expect(screen.getByText(/^指數點數$/i)).toBeInTheDocument();
  });

  it('renders exact 2026-09-30 Flyway benchmark quotes and panic indicators on cards', async () => {
    renderPage();

    // Find the TWII label first
    const twiiHeading = await screen.findByText(/\^TWII 台股加權指數/i);
    expect(twiiHeading).toBeInTheDocument();

    const card = twiiHeading.closest('div[class*="rounded-xl"]');
    await waitFor(() => {
      expect(card?.textContent).not.toContain('--');
    });

    // Verify TWII 47,940.13 and all 5 benchmarks
    expect(card?.textContent).toContain('47,940.13');
    expect(screen.getByText(/7,651\.54/i)).toBeInTheDocument();
    expect(screen.getByText(/30,408\.50/i)).toBeInTheDocument();
    expect(screen.getByText(/12,628\.62/i)).toBeInTheDocument();
    expect(screen.getByText(/66,753\.72/i)).toBeInTheDocument();

    // Verify trade date 2026-09-30
    const dateElements = screen.getAllByText(/交易日:\s*2026-09-30/i);
    expect(dateElements.length).toBeGreaterThan(0);

    // Verify panic indicators
    await waitFor(() => {
      expect(screen.getAllByText(/16\.34/i).length).toBeGreaterThanOrEqual(1);
      expect(screen.getAllByText(/30\.83/i).length).toBeGreaterThanOrEqual(1);
    });
  });
});
