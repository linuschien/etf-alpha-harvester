import { render, screen, waitFor } from '@testing-library/react';
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
const navigate = vi.fn();
const testHandlers = {
  navigate,
  openModal,
  closeModal,
  selectTab,
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
  vi.clearAllMocks();
});

describe('GlobalMarketIntelligencePage', () => {
  // ── Pattern 1 — Render ───────────────────────────────────────────────────
  it('renders page heading and core tabs', async () => {
    renderPage();
    expect(
      await screen.findByRole('heading', {
        name: /全球市場情報/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Tab 1：全球宏觀與情緒雷達/i })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Tab 2：合規標的天梯榜與正交雷達/i })
    ).toBeInTheDocument();
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

    // Alternative row should initially not be visible
    expect(screen.queryByText('富邦台50')).not.toBeInTheDocument();

    // Click toggle button to expand cluster
    const expandBtn = screen.getByRole('button', { name: /切換折疊/i });
    await user.click(expandBtn);

    // Alternative row should now be visible with R² badge
    expect(await screen.findByText('富邦台50')).toBeInTheDocument();
    expect(screen.getByText('006208')).toBeInTheDocument();
    expect(screen.getByText(/R²: 99.8%/i)).toBeInTheDocument();
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
});
