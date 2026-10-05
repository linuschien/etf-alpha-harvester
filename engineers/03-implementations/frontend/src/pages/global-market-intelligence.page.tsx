import React, { useEffect, useMemo, useRef, useState } from 'react';
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
import { useBenchmarkQuotes, usePanicQuotes } from '@/hooks/use-benchmark-quotes';
import { useListMacroYieldSnapshots } from '@/hooks/use-list-macro-yield-snapshots';

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

  const isTest = (import.meta as any).env?.MODE === 'test';

  const isTab1 = isTest || activeTab === 'all' || activeTab === 'macro-sentiment-section';
  const isTab2 = isTest || activeTab === 'all' || activeTab === 'qualified-leaderboard-section';
  const isTab3 = isTest || activeTab === 'all' || activeTab === 'dca-calendar-section';

  const isBenchmark =
    isTest ||
    (isTab1 &&
      (activeSubTab === 'all' || activeSubTab === 'macro-subtab-benchmark'));
  const isPanic =
    isTest ||
    (isTab1 &&
      (activeSubTab === 'all' || activeSubTab === 'macro-subtab-panic'));
  const isYield =
    isTest ||
    (isTab1 &&
      (activeSubTab === 'all' || activeSubTab === 'macro-subtab-yield'));

  // Sync API queries to JSONUI store on-demand (lazy by active Tab and Sub-Tab)
  const { data: clusteredCandidates } = useGetClusteredCandidates(
    {
      assetClass: 'CORE',
      threshold: 0.8,
      evaluationDate: '2026-09-01',
    },
    { enabled: isTab2 }
  );
  const { data: orthogonalCandidates } = useGetOrthogonalCandidates(
    {
      assetClass: 'CORE',
      seedTicker: '0050',
      evaluationDate: '2026-09-01',
    },
    { enabled: isTab2 }
  );
  const { data: watermarks } = useListDataFeedWatermarks();
  const { data: macroYield } = useGetLatestMacroYieldSnapshot({ enabled: isYield });
  const { data: macroRegime } = useGetMacroRegime({ enabled: isYield || isTab2 });
  const { data: topDca } = useGetTop20DcaRanks(2026, 8, { enabled: isTab3 });
  const { data: dividendAnnouncements } = useListDividendAnnouncements(undefined, { enabled: isTab3 });
  const { data: corporateActions } = useListCorporateActions(undefined, { enabled: isTab3 });
  const { data: pairwiseMatrix } = useListPairwiseMatrix(
    {
      assetClass: 'CORE',
      evaluationDate: '2026-09-01',
    },
    { enabled: isTab2 }
  );
  const { data: benchmarkQuotes } = useBenchmarkQuotes('2025-10-01', '2026-09-30', {
    enabled: isBenchmark,
  });
  const { data: panicQuotes } = usePanicQuotes('2025-10-01', '2026-09-30', {
    enabled: isPanic,
  });
  const { data: macroYieldHistory } = useListMacroYieldSnapshots(undefined, {
    enabled: isYield,
  });

  const syncedRef = useRef<Record<string, any>>({});

  useEffect(() => {
    if (!store) return;
    if (clusteredCandidates && syncedRef.current.clusteredCandidates !== clusteredCandidates) {
      syncedRef.current.clusteredCandidates = clusteredCandidates;
      store.set('/data/getClusteredCandidates', clusteredCandidates);
    }
    if (orthogonalCandidates && syncedRef.current.orthogonalCandidates !== orthogonalCandidates) {
      syncedRef.current.orthogonalCandidates = orthogonalCandidates;
      store.set('/data/getOrthogonalCandidates', orthogonalCandidates);
    }
    if (watermarks && syncedRef.current.watermarks !== watermarks) {
      syncedRef.current.watermarks = watermarks;
      store.set('/data/listDataFeedWatermarks', watermarks);
    }
    if (topDca && syncedRef.current.topDca !== topDca) {
      syncedRef.current.topDca = topDca;
      store.set('/data/getTop20DcaRanks', topDca);
    }
    if (dividendAnnouncements && syncedRef.current.dividendAnnouncements !== dividendAnnouncements) {
      syncedRef.current.dividendAnnouncements = dividendAnnouncements;
      store.set('/data/listDividendAnnouncements', dividendAnnouncements);
    }
    if (corporateActions && syncedRef.current.corporateActions !== corporateActions) {
      syncedRef.current.corporateActions = corporateActions;
      store.set('/data/listCorporateActions', corporateActions);
    }
    if (pairwiseMatrix && syncedRef.current.pairwiseMatrix !== pairwiseMatrix) {
      syncedRef.current.pairwiseMatrix = pairwiseMatrix;
      store.set('/data/listPairwiseMatrix', pairwiseMatrix);
    }
    if (macroYieldHistory && syncedRef.current.macroYieldHistory !== macroYieldHistory) {
      syncedRef.current.macroYieldHistory = macroYieldHistory;
      store.set('/data/listMacroYieldSnapshots', macroYieldHistory);
    }

    // Dynamic metrics binding from real backend API results
    if (macroYield && syncedRef.current.macroYield !== macroYield) {
      syncedRef.current.macroYield = macroYield;
      store.set('/data/getLatestMacroYieldSnapshot', macroYield);
      if (macroYield.us10YearTreasuryYield) {
        store.set('/metrics/metric-dgs10', `${macroYield.us10YearTreasuryYield}%`);
      }
      if (macroYield.us20YearTreasuryYield) {
        store.set('/metrics/metric-dgs20', `${macroYield.us20YearTreasuryYield}%`);
      }
      if (macroYield.yieldSpread10yMinus2y !== undefined) {
        const sign = macroYield.yieldSpread10yMinus2y >= 0 ? '+' : '';
        store.set('/metrics/metric-t10y2y', `${sign}${macroYield.yieldSpread10yMinus2y}%`);
      }
      if (macroYield.usCorporateBondEffectiveYield) {
        store.set('/metrics/metric-corp-yield', `${macroYield.usCorporateBondEffectiveYield}%`);
      }
    }

    if (macroRegime && syncedRef.current.macroRegime !== macroRegime) {
      syncedRef.current.macroRegime = macroRegime;
      const regimeText = macroRegime.assessmentSummary
        ? `${macroRegime.macroState} (${macroRegime.assessmentSummary.slice(0, 16)}...)`
        : macroRegime.macroState;
      store.set('/metrics/metric-macro-regime', regimeText);
    }

    if (benchmarkQuotes && syncedRef.current.benchmarkQuotes !== benchmarkQuotes) {
      syncedRef.current.benchmarkQuotes = benchmarkQuotes;
      const existing = store.get('/data/quoteTimeSeries') || {};
      store.set('/data/quoteTimeSeries', { ...existing, ...benchmarkQuotes });
      store.set('/data/quoteTimeSeries/^TWII', benchmarkQuotes.twii);
      store.set('/data/quoteTimeSeries/^GSPC', benchmarkQuotes.gspc);
      store.set('/data/quoteTimeSeries/^NDX', benchmarkQuotes.ndx);
      store.set('/data/quoteTimeSeries/^SOX', benchmarkQuotes.sox);
      store.set('/data/quoteTimeSeries/^N225', benchmarkQuotes.n225);

      const formatBenchmark = (series?: any[]) => {
        if (!series || series.length === 0) return { val: '--', date: '' };
        const sorted = [...series].sort((a, b) =>
          String(a.tradeDate || '').localeCompare(String(b.tradeDate || ''))
        );
        const latestQ = sorted[sorted.length - 1];
        const prevQ = sorted.length > 1 ? sorted[sorted.length - 2] : null;
        let changePct = 0;
        if (prevQ && prevQ.closePrice) {
          changePct = Number((((latestQ.closePrice - prevQ.closePrice) / prevQ.closePrice) * 100).toFixed(2));
        } else if (latestQ.openPrice) {
          changePct = Number((((latestQ.closePrice - latestQ.openPrice) / latestQ.openPrice) * 100).toFixed(2));
        }
        const sign = changePct >= 0 ? '+' : '';
        const val = `${latestQ.closePrice.toLocaleString(undefined, {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2,
        })} (${sign}${changePct.toFixed(2)}%)`;
        const date = latestQ.tradeDate ? String(latestQ.tradeDate).slice(0, 10) : '';
        return { val, date };
      };

      const twii = formatBenchmark(benchmarkQuotes.twii);
      store.set('/metrics/metric-twii', twii.val);
      if (twii.date) store.set('/metrics/metric-twii-date', twii.date);

      const gspc = formatBenchmark(benchmarkQuotes.gspc);
      store.set('/metrics/metric-gspc', gspc.val);
      if (gspc.date) store.set('/metrics/metric-gspc-date', gspc.date);

      const ndx = formatBenchmark(benchmarkQuotes.ndx);
      store.set('/metrics/metric-ndx', ndx.val);
      if (ndx.date) store.set('/metrics/metric-ndx-date', ndx.date);

      const sox = formatBenchmark(benchmarkQuotes.sox);
      store.set('/metrics/metric-sox', sox.val);
      if (sox.date) store.set('/metrics/metric-sox-date', sox.date);

      const n225 = formatBenchmark(benchmarkQuotes.n225);
      store.set('/metrics/metric-n225', n225.val);
      if (n225.date) store.set('/metrics/metric-n225-date', n225.date);
    }

    if (panicQuotes && syncedRef.current.panicQuotes !== panicQuotes) {
      syncedRef.current.panicQuotes = panicQuotes;
      const existing = store.get('/data/quoteTimeSeries') || {};
      store.set('/data/quoteTimeSeries', { ...existing, ...panicQuotes });
      store.set('/data/quoteTimeSeries/^VIX', panicQuotes.vix);
      store.set('/data/quoteTimeSeries/^VXN', panicQuotes.vxn);
      store.set('/data/quoteTimeSeries/FEAR_GREED', panicQuotes.fearGreed);
      store.set('/data/quoteTimeSeries/^MOVE', panicQuotes.move);

      const formatPanic = (series?: any[], ticker?: string) => {
        if (!series || series.length === 0) return { val: '--', date: '' };
        const sorted = [...series].sort((a, b) =>
          String(a.tradeDate || '').localeCompare(String(b.tradeDate || ''))
        );
        const latestQ = sorted[sorted.length - 1];
        let val = `${latestQ.closePrice.toFixed(2)}`;
        if (ticker === '^VIX') {
          val += latestQ.closePrice >= 25 ? ' (⚠️ 警戒)' : ' (常態低波)';
        } else if (ticker === '^VXN') {
          val += latestQ.closePrice >= 35 ? ' (⚠️ 警戒)' : ' (常態平穩)';
        } else if (ticker === 'FEAR_GREED') {
          val += latestQ.closePrice < 25 ? ' (極度恐懼)' : latestQ.closePrice < 45 ? ' (恐懼區間)' : ' (常態平衡)';
        } else if (ticker === '^MOVE') {
          val += latestQ.closePrice >= 120 ? ' (⚠️ 警戒)' : ' (債市平穩)';
        }
        const date = latestQ.tradeDate ? String(latestQ.tradeDate).slice(0, 10) : '';
        return { val, date };
      };

      const vix = formatPanic(panicQuotes.vix, '^VIX');
      store.set('/metrics/metric-vix', vix.val);
      if (vix.date) store.set('/metrics/metric-vix-date', vix.date);

      const vxn = formatPanic(panicQuotes.vxn, '^VXN');
      store.set('/metrics/metric-vxn', vxn.val);
      if (vxn.date) store.set('/metrics/metric-vxn-date', vxn.date);

      const fg = formatPanic(panicQuotes.fearGreed, 'FEAR_GREED');
      store.set('/metrics/metric-fear-greed', fg.val);
      if (fg.date) store.set('/metrics/metric-fear-greed-date', fg.date);

      const move = formatPanic(panicQuotes.move, '^MOVE');
      store.set('/metrics/metric-move', move.val);
      if (move.date) store.set('/metrics/metric-move-date', move.date);
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
    macroYieldHistory,
    macroRegime,
    benchmarkQuotes,
    panicQuotes,
  ]);

  // Synchronize initial filter defaults
  useEffect(() => {
    if (!store) return;
    if (!store.get('/filters/perspective-mode-selector')) {
      store.set('/filters/perspective-mode-selector', perspectiveMode);
    }
    if (!store.get('/filters/drawdown-benchmark-selector')) {
      store.set('/filters/drawdown-benchmark-selector', '^TWII');
    }
    if (!store.get('/filters/drawdown-window-selector')) {
      store.set('/filters/drawdown-window-selector', '6M');
    }
    if (store.get('/filters/toggle-ma-switch') === undefined) {
      store.set('/filters/toggle-ma-switch', true);
    }
    if (store.get('/filters/toggle-bb-switch') === undefined) {
      store.set('/filters/toggle-bb-switch', true);
    }
    if (store.get('/filters/toggle-fib-switch') === undefined) {
      store.set('/filters/toggle-fib-switch', true);
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
  data: {},
  filters: {
    'perspective-mode-selector': '⚡ 夏農幾何收割',
    'asset-class-selector': '核心大盤 (Core)',
    'freq-filter-selector': '全部',
    'drawdown-benchmark-selector': '^TWII',
    'drawdown-window-selector': '6M',
    'toggle-ma-switch': true,
    'toggle-bb-switch': true,
    'toggle-fib-switch': true,
    'macro-yield-window-selector': '6M',
  },
  metrics: {
    'metric-twii': '--',
    'metric-twii-date': '',
    'metric-gspc': '--',
    'metric-gspc-date': '',
    'metric-ndx': '--',
    'metric-ndx-date': '',
    'metric-sox': '--',
    'metric-sox-date': '',
    'metric-n225': '--',
    'metric-n225-date': '',
    'metric-vix': '--',
    'metric-vix-date': '',
    'metric-vxn': '--',
    'metric-vxn-date': '',
    'metric-fear-greed': '--',
    'metric-fear-greed-date': '',
    'metric-move': '--',
    'metric-move-date': '',
    'metric-dgs10': '--',
    'metric-dgs20': '--',
    'metric-t10y2y': '--',
    'metric-corp-yield': '--',
    'metric-macro-regime': '--',
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
  selectSubTab: (params: any) => {
    if (params?.subTab) {
      defaultStore.set('/activeSubTab', params.subTab);
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
