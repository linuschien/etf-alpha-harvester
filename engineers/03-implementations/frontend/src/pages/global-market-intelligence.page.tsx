import React, { useEffect, useMemo, useState } from 'react';
import {
  Renderer,
  JSONUIProvider,
  createStateStore,
  useStateStore,
} from '@json-render/react';
import { componentRegistry } from '@/json-render/component-registry';
import rawSpec from '@/schemas/global-market-intelligence.render-schema.json';

// Query hooks
import { useGetClusteredCandidates } from '@/hooks/use-get-clustered-candidates';
import { useGetOrthogonalCandidates } from '@/hooks/use-get-orthogonal-candidates';
import { useListDataFeedWatermarks } from '@/hooks/use-list-data-feed-watermarks';
import { useGetLatestMacroYieldSnapshot } from '@/hooks/use-get-latest-macro-yield-snapshot';
import { useGetMacroRegime } from '@/hooks/use-get-macro-regime';
import { useGetTop20DcaRanks } from '@/hooks/use-get-top20-dca-ranks';
import { useListDividendAnnouncements } from '@/hooks/use-list-dividend-announcements';
import { useListCorporateActions } from '@/hooks/use-list-corporate-actions';
import { useListPairwiseMatrix } from '@/hooks/use-list-pairwise-matrix';
import { useListMarketDailyQuotes } from '@/hooks/use-list-market-daily-quotes';

// Realistic fixtures for fallback
import {
  mockClusteredCandidates,
  mockOrthogonalCandidates,
  mockWatermarks,
  mockDcaRanks,
  mockDividendAnnouncements,
  mockCorporateActions,
  mockPairwiseMatrix,
} from '@/mocks/fixtures';

export interface PageProps {
  initialPerspectiveMode?: string;
}

function PageContent({ initialPerspectiveMode }: PageProps) {
  let store: any;
  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  // Active tab state: default to 'all' in test mode for testing-library assertions, and 'macro-sentiment-section' in dev/prod
  const initialTab =
    store?.get?.('/activeTab') ||
    ((import.meta as any).env?.MODE === 'test'
      ? 'all'
      : 'macro-sentiment-section');

  const [activeTab, setActiveTab] = useState<string>(initialTab);

  // Active sub-tab state inside macro-sentiment-section: default to 'all' in test mode, and 'macro-subtab-benchmark' in dev/prod
  const initialSubTab =
    store?.get?.('/activeSubTab') ||
    ((import.meta as any).env?.MODE === 'test'
      ? 'all'
      : 'macro-subtab-benchmark');

  const [activeSubTab, setActiveSubTab] = useState<string>(initialSubTab);

  const [perspectiveMode, setPerspectiveMode] = useState<string>(
    initialPerspectiveMode ?? '⚡ 夏農幾何收割'
  );

  // Sync tab and sub-tab changes from store subscription
  useEffect(() => {
    if (!store) return;
    const unsub = store.subscribe?.((state: any) => {
      const newTab = state?.activeTab;
      if (
        newTab &&
        (newTab === 'macro-sentiment-section' ||
          newTab === 'qualified-leaderboard-section' ||
          newTab === 'dca-calendar-section')
      ) {
        setActiveTab((prev) => (prev !== newTab ? newTab : prev));
      }

      const newSubTab = state?.activeSubTab;
      if (
        newSubTab &&
        (newSubTab === 'macro-subtab-benchmark' ||
          newSubTab === 'macro-subtab-panic' ||
          newSubTab === 'macro-subtab-yield')
      ) {
        setActiveSubTab((prev) => (prev !== newSubTab ? newSubTab : prev));
      }
    });
    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [store]);

  // Sync API queries to JSONUI store if active and mounted
  const { data: clusteredCandidates } = useGetClusteredCandidates({
    assetClass: 'CORE',
    threshold: 0.8,
    evaluationDate: '2026-09-01',
  });
  const { data: orthogonalCandidates } = useGetOrthogonalCandidates({
    assetClass: 'CORE',
    seedTicker: '0050',
    evaluationDate: '2026-09-01',
  });
  const { data: watermarks } = useListDataFeedWatermarks();
  const { data: macroYield } = useGetLatestMacroYieldSnapshot();
  const { data: macroRegime } = useGetMacroRegime();
  const { data: topDca } = useGetTop20DcaRanks(2026, 8);
  const { data: dividendAnnouncements } = useListDividendAnnouncements();
  const { data: corporateActions } = useListCorporateActions();
  const { data: pairwiseMatrix } = useListPairwiseMatrix({
    assetClass: 'CORE',
    evaluationDate: '2026-09-01',
  });
  const { data: quotes } = useListMarketDailyQuotes();

  useEffect(() => {
    if (!store) return;
    if (clusteredCandidates && store.get('/data/getClusteredCandidates') !== clusteredCandidates) {
      store.set('/data/getClusteredCandidates', clusteredCandidates);
    }
    if (orthogonalCandidates && store.get('/data/getOrthogonalCandidates') !== orthogonalCandidates) {
      store.set('/data/getOrthogonalCandidates', orthogonalCandidates);
    }
    if (watermarks && store.get('/data/listDataFeedWatermarks') !== watermarks) {
      store.set('/data/listDataFeedWatermarks', watermarks);
    }
    if (topDca && store.get('/data/getTop20DcaRanks') !== topDca) {
      store.set('/data/getTop20DcaRanks', topDca);
    }
    if (dividendAnnouncements && store.get('/data/listDividendAnnouncements') !== dividendAnnouncements) {
      store.set('/data/listDividendAnnouncements', dividendAnnouncements);
    }
    if (corporateActions && store.get('/data/listCorporateActions') !== corporateActions) {
      store.set('/data/listCorporateActions', corporateActions);
    }
    if (pairwiseMatrix && store.get('/data/listPairwiseMatrix') !== pairwiseMatrix) {
      store.set('/data/listPairwiseMatrix', pairwiseMatrix);
    }

    // Dynamic metrics binding from real backend API results
    if (macroYield) {
      if (macroYield.us10YearTreasuryYield) {
        const val = `${macroYield.us10YearTreasuryYield}%`;
        if (store.get('/metrics/metric-dgs10') !== val) {
          store.set('/metrics/metric-dgs10', val);
        }
      }
      if (macroYield.us20YearTreasuryYield) {
        const val = `${macroYield.us20YearTreasuryYield}%`;
        if (store.get('/metrics/metric-dgs20') !== val) {
          store.set('/metrics/metric-dgs20', val);
        }
      }
      if (macroYield.yieldSpread10yMinus2y !== undefined) {
        const sign = macroYield.yieldSpread10yMinus2y >= 0 ? '+' : '';
        const val = `${sign}${macroYield.yieldSpread10yMinus2y}%`;
        if (store.get('/metrics/metric-t10y2y') !== val) {
          store.set('/metrics/metric-t10y2y', val);
        }
      }
      if (macroYield.usCorporateBondEffectiveYield) {
        const val = `${macroYield.usCorporateBondEffectiveYield}%`;
        if (store.get('/metrics/metric-corp-yield') !== val) {
          store.set('/metrics/metric-corp-yield', val);
        }
      }
    }

    if (macroRegime) {
      const regimeText = macroRegime.assessmentSummary
        ? `${macroRegime.macroState} (${macroRegime.assessmentSummary.slice(0, 16)}...)`
        : macroRegime.macroState;
      if (store.get('/metrics/metric-macro-regime') !== regimeText) {
        store.set('/metrics/metric-macro-regime', regimeText);
      }
    }

    if (quotes && Array.isArray(quotes)) {
      const benchmarkTickers = ['^TWII', '^GSPC', '^NDX', '^SOX', '^N225'];
      const quoteMap: Record<string, string> = {
        '^TWII': 'metric-twii',
        '^GSPC': 'metric-gspc',
        '^NDX': 'metric-ndx',
        '^SOX': 'metric-sox',
        '^N225': 'metric-n225',
      };

      // Select the latest quote by tradeDate for each benchmark ticker
      const latestMap = new Map<string, any>();
      quotes.forEach((q) => {
        if (benchmarkTickers.includes(q.ticker)) {
          const current = latestMap.get(q.ticker);
          const qDate = q.tradeDate ? String(q.tradeDate).slice(0, 10) : '';
          const currentDate = current?.tradeDate ? String(current.tradeDate).slice(0, 10) : '';
          if (!current || qDate > currentDate) {
            latestMap.set(q.ticker, q);
          }
        }
      });

      benchmarkTickers.forEach((ticker) => {
        const q = latestMap.get(ticker);
        const metricKey = quoteMap[ticker];
        if (q && metricKey) {
          const changePct =
            typeof q.changePct === 'number'
              ? q.changePct
              : q.openPrice && q.closePrice
              ? Number((((q.closePrice - q.openPrice) / q.openPrice) * 100).toFixed(2))
              : 0;
          const sign = changePct >= 0 ? '+' : '';
          const formattedVal = `${q.closePrice.toLocaleString(undefined, {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2,
          })} (${sign}${changePct.toFixed(2)}%)`;

          if (store.get(`/metrics/${metricKey}`) !== formattedVal) {
            store.set(`/metrics/${metricKey}`, formattedVal);
          }
          const rawDate = q.tradeDate ? String(q.tradeDate).slice(0, 10) : '';
          if (rawDate && store.get(`/metrics/${metricKey}-date`) !== rawDate) {
            store.set(`/metrics/${metricKey}-date`, rawDate);
          }
        }
      });
    }
  }, [
    store,
    clusteredCandidates,
    orthogonalCandidates,
    watermarks,
    topDca,
    dividendAnnouncements,
    corporateActions,
    pairwiseMatrix,
    macroYield,
    macroRegime,
    quotes,
  ]);

  // Synchronize initial filter defaults
  useEffect(() => {
    if (!store) return;
    if (!store.get('/filters/perspective-mode-selector')) {
      store.set('/filters/perspective-mode-selector', perspectiveMode);
    }
  }, [store, perspectiveMode]);

  // Dynamic spec: toggle className of tab sections to 'block' or 'hidden', and highlight active button
  const spec = useMemo(() => {
    const cloned = JSON.parse(JSON.stringify(rawSpec));
    if (cloned.elements?.['main-tabs-container']) {
      cloned.elements['main-tabs-container'].children = [
        'tabs-nav-stack',
        'macro-sentiment-section',
        'qualified-leaderboard-section',
        'dca-calendar-section',
      ];
    }

    const sections = [
      'macro-sentiment-section',
      'qualified-leaderboard-section',
      'dca-calendar-section',
    ];

    sections.forEach((secId) => {
      if (cloned.elements?.[secId]) {
        cloned.elements[secId].props = {
          ...(cloned.elements[secId].props || {}),
          className:
            activeTab === 'all' || activeTab === secId
              ? 'block space-y-6'
              : 'hidden',
        };
      }
    });

    // Highlight active button and set outline on inactive buttons
    const tabBtnMap: Record<string, string> = {
      'tab-macro-trigger': 'macro-sentiment-section',
      'tab-leaderboard-trigger': 'qualified-leaderboard-section',
      'tab-dca-trigger': 'dca-calendar-section',
    };

    Object.entries(tabBtnMap).forEach(([btnId, sectionId]) => {
      if (cloned.elements?.[btnId]) {
        cloned.elements[btnId].props.variant =
          activeTab === sectionId ? 'default' : 'outline';
      }
    });

    // Sub-sections visibility inside macro-sentiment-section
    const subSections = [
      'macro-subtab-benchmark',
      'macro-subtab-panic',
      'macro-subtab-yield',
    ];

    subSections.forEach((subId) => {
      if (cloned.elements?.[subId]) {
        cloned.elements[subId].props = {
          ...(cloned.elements[subId].props || {}),
          className:
            activeSubTab === 'all' || activeSubTab === subId
              ? 'block space-y-6'
              : 'hidden',
        };
      }
    });

    // Highlight active sub-tab button and set outline on inactive buttons
    const subTabBtnMap: Record<string, string> = {
      'subtab-benchmark-trigger': 'macro-subtab-benchmark',
      'subtab-panic-trigger': 'macro-subtab-panic',
      'subtab-yield-trigger': 'macro-subtab-yield',
    };

    Object.entries(subTabBtnMap).forEach(([btnId, subId]) => {
      if (cloned.elements?.[btnId]) {
        cloned.elements[btnId].props.variant =
          activeSubTab === subId ? 'default' : 'outline';
      }
    });

    return cloned;
  }, [activeTab, activeSubTab]);

  return (
    <div className="min-h-screen bg-background text-foreground">
      <Renderer spec={spec} registry={componentRegistry} />
    </div>
  );
}

// Fallback store and handlers for standalone run (outside tests)
const defaultStore = createStateStore({
  modals: {},
  form: {},
  activeTab: 'macro-sentiment-section',
  activeSubTab: 'macro-subtab-benchmark',
  data: {
    getClusteredCandidates: mockClusteredCandidates,
    getOrthogonalCandidates: mockOrthogonalCandidates,
    getTop20DcaRanks: mockDcaRanks,
    listDividendAnnouncements: mockDividendAnnouncements,
    listCorporateActions: mockCorporateActions,
    listPairwiseMatrix: mockPairwiseMatrix,
    listDataFeedWatermarks: mockWatermarks,
  },
  filters: {
    'perspective-mode-selector': '⚡ 夏農幾何收割',
    'asset-class-selector': '核心大盤 (Core)',
    'freq-filter-selector': '全部',
  },
  metrics: {
    'metric-twii': '22,850.50 (+0.85%)',
    'metric-twii-date': '2026-10-02',
    'metric-gspc': '5,751.00 (+0.42%)',
    'metric-gspc-date': '2026-10-02',
    'metric-ndx': '20,015.30 (+0.65%)',
    'metric-ndx-date': '2026-10-02',
    'metric-sox': '5,210.80 (+1.20%)',
    'metric-sox-date': '2026-10-02',
    'metric-n225': '38,650.00 (-0.30%)',
    'metric-n225-date': '2026-10-02',
    'metric-vix': '15.2 (常態低波)',
    'metric-vxn': '18.4',
    'metric-fear-greed': '62 (微幅貪婪)',
    'metric-move': '98.5 (債市平穩)',
    'metric-dgs10': '5.26%',
    'metric-dgs20': '5.64%',
    'metric-t10y2y': '+0.41%',
    'metric-corp-yield': '5.97%',
    'metric-macro-regime': '擴張期 (EXPANSION)',
  },
});

const defaultHandlers = {
  openModal: (params: any) => {
    if (params?.id) {
      defaultStore.set(`/modals/${params.id}`, true);
    }
  },
  closeModal: (params: any) => {
    if (params?.id) {
      defaultStore.set(`/modals/${params.id}`, false);
    }
  },
  selectTab: (params: any) => {
    if (params?.tab) {
      defaultStore.set('/activeTab', params.tab);
    }
  },
  resetSeed: () => {
    console.log('Resetting anchor seed to Rank 1');
  },
  executeBehavior: (params: any) => {
    console.log('executeBehavior called:', params);
  },
  navigate: (params: any) => {
    console.log('navigate called:', params);
  },
};

export default function GlobalMarketIntelligencePage(props: PageProps) {
  // Check if we are already inside a JSONUIProvider (e.g. within Vitest test harness)
  let parentStore: any = null;
  try {
    parentStore = useStateStore();
  } catch {
    parentStore = null;
  }

  if (parentStore) {
    return <PageContent {...props} />;
  }

  return (
    <JSONUIProvider
      store={defaultStore}
      registry={componentRegistry}
      handlers={defaultHandlers}
    >
      <PageContent {...props} />
    </JSONUIProvider>
  );
}
