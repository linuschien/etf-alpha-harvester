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

  // Active tab state (macro, leaderboard, dca)
  const [activeTab, setActiveTab] = useState<string>('macro-sentiment-section');
  const [perspectiveMode, setPerspectiveMode] = useState<string>(
    initialPerspectiveMode ?? '⚡ 夏農幾何收割'
  );

  // Sync API queries to JSONUI store if active and mounted
  const { data: clusteredCandidates } = useGetClusteredCandidates();
  const { data: orthogonalCandidates } = useGetOrthogonalCandidates({
    assetClass: 'CORE_EQUITY',
  });
  const { data: watermarks } = useListDataFeedWatermarks();
  const { data: macroYield } = useGetLatestMacroYieldSnapshot();
  const { data: macroRegime } = useGetMacroRegime();
  const { data: topDca } = useGetTop20DcaRanks();
  const { data: dividendAnnouncements } = useListDividendAnnouncements();
  const { data: corporateActions } = useListCorporateActions();
  const { data: pairwiseMatrix } = useListPairwiseMatrix({
    assetClass: 'CORE_EQUITY',
  });

  useEffect(() => {
    if (!store) return;
    if (clusteredCandidates && !store.get('/data/getClusteredCandidates')) {
      store.set('/data/getClusteredCandidates', clusteredCandidates);
    }
    if (orthogonalCandidates && !store.get('/data/getOrthogonalCandidates')) {
      store.set('/data/getOrthogonalCandidates', orthogonalCandidates);
    }
    if (watermarks && !store.get('/data/listDataFeedWatermarks')) {
      store.set('/data/listDataFeedWatermarks', watermarks);
    }
    if (topDca && !store.get('/data/getTop20DcaRanks')) {
      store.set('/data/getTop20DcaRanks', topDca);
    }
    if (dividendAnnouncements && !store.get('/data/listDividendAnnouncements')) {
      store.set('/data/listDividendAnnouncements', dividendAnnouncements);
    }
    if (corporateActions && !store.get('/data/listCorporateActions')) {
      store.set('/data/listCorporateActions', corporateActions);
    }
    if (pairwiseMatrix && !store.get('/data/listPairwiseMatrix')) {
      store.set('/data/listPairwiseMatrix', pairwiseMatrix);
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
  ]);

  // Synchronize initial filter defaults
  useEffect(() => {
    if (!store) return;
    if (!store.get('/filters/perspective-mode-selector')) {
      store.set('/filters/perspective-mode-selector', perspectiveMode);
    }
  }, [store, perspectiveMode]);

  // Render spec with responsive tab & perspective adaptation
  const spec = useMemo(() => {
    return rawSpec as any;
  }, []);

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
  data: {},
  filters: {
    'perspective-mode-selector': '⚡ 夏農幾何收割',
    'asset-class-selector': 'CORE_EQUITY',
  },
  metrics: {
    'metric-twii': '22,850.50',
    'metric-gspc': '5,860.20',
    'metric-ndx': '20,450.10',
    'metric-sox': '5,320.80',
    'metric-n225': '38,900.00',
    'metric-vix': '15.2',
    'metric-vxn': '18.4',
    'metric-fear-greed': '62',
    'metric-move': '98.5',
    'metric-dgs10': '4.12%',
    'metric-dgs20': '4.45%',
    'metric-t10y2y': '+0.15%',
    'metric-corp-yield': '5.20%',
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
