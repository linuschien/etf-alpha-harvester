// Global seed data fixtures for MSW handlers and unit tests

export const mockClusteredCandidates: any[] = [
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
  {
    clusterId: 2,
    isSingleton: true,
    leader: {
      ticker: '0056',
      name: '元大高股息',
      underlyingIndex: '臺灣高股息指數',
      closePrice: 38.6,
      changePct: -0.25,
      return1m: 1.2,
      return3m: 4.1,
      return6m: 8.2,
      return1y: 18.5,
      fundSizeTwd: 310000000000,
      compositeScore: 84.1,
    },
    alternatives: [],
  },
];

export const mockOrthogonalCandidates: any[] = [
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
  {
    classRank: 2,
    orthogonalStatus: '正交合規',
    ticker: '00713',
    name: '元大台灣高息低波',
    distributionFrequency: '季配',
    closePrice: 57.3,
    changePct: 0.35,
    return1m: 2.1,
    return3m: 5.6,
    return6m: 11.2,
    return1y: 22.8,
    fundSizeTwd: 95000000000,
    compositeScore: 88.7,
  },
];

export const mockWatermarks: any[] = [
  {
    feedName: 'TWSE_DAILY_QUOTE',
    status: 'HEALTHY',
    latestRecordDate: '2026-10-02',
    recordsSyncedCount: 2850,
    lastSuccessfulSyncAt: '2026-10-02T16:00:00Z',
    updatedAt: '2026-10-02T16:05:00Z',
  },
  {
    feedName: 'FRED_MACRO_YIELD',
    status: 'HEALTHY',
    latestRecordDate: '2026-10-01',
    recordsSyncedCount: 120,
    lastSuccessfulSyncAt: '2026-10-02T08:00:00Z',
    updatedAt: '2026-10-02T08:05:00Z',
  },
];

export const mockDcaRanks: any[] = [
  {
    rankPosition: 1,
    ticker: '0050',
    name: '元大台灣50',
    distributionFrequency: '半年配',
    investorCount: 265000,
    rankingYear: 2026,
    rankingMonth: 9,
  },
  {
    rankPosition: 2,
    ticker: '0056',
    name: '元大高股息',
    distributionFrequency: '季配',
    investorCount: 242000,
    rankingYear: 2026,
    rankingMonth: 9,
  },
];

export const mockDividendAnnouncements: any[] = [
  {
    ticker: '0056',
    exDate: '2026-10-18',
    dividendPerShare: 1.07,
    paymentDate: '2026-11-12',
    taxTag: '54C',
  },
  {
    ticker: '00713',
    exDate: '2026-10-22',
    dividendPerShare: 1.5,
    paymentDate: '2026-11-18',
    taxTag: '76W免二代健保',
  },
];

export const mockCorporateActions: any[] = [
  {
    effectiveDate: '2026-11-01',
    ticker: '00631L',
    actionType: 'STOCK_SPLIT',
    splitRatioNumerator: 2,
    splitRatioDenominator: 1,
  },
];

export const mockPairwiseMatrix: any[] = [
  {
    baseTicker: '0050',
    targetTicker: '006208',
    correlationCoeff: 0.999,
    rSquared: 0.998,
  },
  {
    baseTicker: '0050',
    targetTicker: '00713',
    correlationCoeff: 0.652,
    rSquared: 0.425,
  },
];

export const mockMacroYieldSnapshot = {
  dgs10: 4.12,
  dgs20: 4.45,
  dgs30: 4.6,
  dgs2: 3.97,
  t10y2ySpread: 0.15,
  bamlc0a0cmeyCorpYield: 5.2,
  snapshotDate: '2026-10-02',
};

export const mockMacroRegime = {
  currentRegime: 'EXPANSION',
  confidenceScore: 0.88,
  assessmentSummary: '經濟基本面穩定擴張，利差倒掛解除，信用利差維持低位。',
  effectiveDate: '2026-10-02',
};

// Helper functions for dynamic test mutation
export function setMockClusteredCandidates(data: any[]) {
  mockClusteredCandidates.splice(0, mockClusteredCandidates.length, ...data);
}

export function resetMockClusteredCandidates() {
  mockClusteredCandidates.splice(
    0,
    mockClusteredCandidates.length,
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
    }
  );
}
