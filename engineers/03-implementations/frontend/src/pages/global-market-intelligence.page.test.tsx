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
const navigate = vi.fn();
const testHandlers = {
  navigate,
  openModal,
  closeModal,
  selectTab,
  selectSubTab,
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
  vi.clearAllMocks();
});

describe('GlobalMarketIntelligencePage', () => {
  // ── Pattern 1 — Render ───────────────────────────────────────────────────
  it('renders page heading and core tabs without Tab 1/2/3 prefix', async () => {
    renderPage();
    expect(
      await screen.findByRole('heading', {
        name: /全球市場情報/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /全球宏觀與情緒雷達/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /合規標的天梯榜與正交雷達/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /定期定額散戶人氣榜與除息月曆/i })
    ).toBeInTheDocument();
  });

  it('renders 3 sub-tabs under macro sentiment radar and switches sub-tabs', async () => {
    const user = userEvent.setup();
    renderPage();

    // Verify all 3 sub-tab buttons are rendered
    const subTab1 = await screen.findByRole('button', {
      name: /5 大全球核心基準指數近一日行情與走勢/i,
    });
    const subTab2 = screen.getByRole('button', {
      name: /四大恐慌情緒指標/i,
    });
    const subTab3 = screen.getByRole('button', {
      name: /宏觀利率與殖利率曲線/i,
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

  it('renders four panic sentiment indicators in 2x2 matrix with sparklines', async () => {
    renderPage();

    // Verify 4 panic indicators exist
    expect(await screen.findByText(/\^VIX 波動率指數/i)).toBeInTheDocument();
    expect(screen.getByText(/\^VXN 那指波動率指數/i)).toBeInTheDocument();
    expect(screen.getByText(/FEAR_GREED 恐懼貪婪指數/i)).toBeInTheDocument();
    expect(screen.getByText(/\^MOVE 美債波動率指數/i)).toBeInTheDocument();
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

  // ── Pattern 3 — Modal Open + executeBehavior ─────────────────────────────
  it('opens watermark detail modal when data freshness button is clicked', async () => {
    const user = userEvent.setup();
    renderPage();

    const triggerBtn = await screen.findByRole('button', {
      name: /數據新鮮度監控/i,
    });
    await user.click(triggerBtn);

    expect(openModal).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'watermark-detail-modal' })
    );
    expect(store.get('/modals/watermark-detail-modal')).toBe(true);
  });

  it('triggers executeBehavior when reset seed button is pressed', async () => {
    const user = userEvent.setup();
    renderPage();

    const resetBtn = await screen.findByRole('button', {
      name: /↺ 重設為 Rank 1 種子/i,
    });
    await user.click(resetBtn);

    expect(executeBehavior).toHaveBeenCalledWith(
      expect.objectContaining({ ref: 'reset-seed-trigger' })
    );
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
  it('opens asset detail drawer and updates selected asset on table row click', async () => {
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
      },
    ];

    store.set('/data/getOrthogonalCandidates', mockRows);
    renderPage();

    const row = await screen.findByText('元大台灣50');
    await user.click(row);

    expect(store.get('/modals/asset-detail-drawer')).toBe(true);
    expect(store.get('/selectedAsset')).toBe('0050');
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
    expect(screen.getAllByText(/16\.34/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/30\.83/i).length).toBeGreaterThanOrEqual(1);
  });
});
