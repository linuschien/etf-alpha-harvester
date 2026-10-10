import { http, HttpResponse } from 'msw';
import {
  mockClusteredCandidates,
  mockOrthogonalCandidates,
  mockWatermarks,
  mockDcaRanks,
  mockDividendAnnouncements,
  mockCorporateActions,
  mockPairwiseMatrix,
  mockMacroYieldSnapshot,
  mockMacroRegime,
  mockMarketDailyQuotes,
} from './fixtures';

export const handlers = [
  // Intercept GraphQL requests
  http.post('/graphql', async ({ request }) => {
    const body: any = await request.json();
    const query = body?.query || '';
    const operationName = body?.operationName || '';

    if (query.includes('getClusteredCandidates') || operationName === 'GetClusteredCandidates') {
      return HttpResponse.json({
        data: { getClusteredCandidates: mockClusteredCandidates },
      });
    }

    if (query.includes('getOrthogonalCandidates') || operationName === 'GetOrthogonalCandidates') {
      return HttpResponse.json({
        data: { getOrthogonalCandidates: mockOrthogonalCandidates },
      });
    }

    if (query.includes('listDataFeedWatermarks') || operationName === 'ListDataFeedWatermarks') {
      return HttpResponse.json({
        data: { listDataFeedWatermarks: mockWatermarks },
      });
    }

    if (query.includes('getTop20DcaRanks') || operationName === 'GetTop20DcaRanks') {
      return HttpResponse.json({
        data: { getTop20DcaRanks: mockDcaRanks },
      });
    }

    if (query.includes('listDividendAnnouncements') || operationName === 'ListDividendAnnouncements') {
      const filter = body?.variables?.filter;
      let list = mockDividendAnnouncements;
      if (filter?.ticker) {
        list = list.filter((d: any) => d.ticker === filter.ticker);
      }
      return HttpResponse.json({
        data: { listDividendAnnouncements: list },
      });
    }

    if (query.includes('listCorporateActions') || operationName === 'ListCorporateActions') {
      return HttpResponse.json({
        data: { listCorporateActions: mockCorporateActions },
      });
    }

    if (query.includes('listPairwiseMatrix') || operationName === 'ListPairwiseMatrix') {
      return HttpResponse.json({
        data: { listPairwiseMatrix: mockPairwiseMatrix },
      });
    }

    if (query.includes('getLatestMacroYieldSnapshot') || operationName === 'GetLatestMacroYieldSnapshot') {
      return HttpResponse.json({
        data: { getLatestMacroYieldSnapshot: mockMacroYieldSnapshot },
      });
    }

    if (query.includes('getMacroRegime') || operationName === 'GetMacroRegime') {
      return HttpResponse.json({
        data: { getMacroRegime: mockMacroRegime },
      });
    }

    if (query.includes('GetBenchmarkQuotes') || operationName === 'GetBenchmarkQuotes') {
      const byTicker = (t: string) => mockMarketDailyQuotes.filter((q) => q.ticker === t);
      return HttpResponse.json({
        data: {
          twii: byTicker('^TWII'),
          gspc: byTicker('^GSPC'),
          ndx: byTicker('^NDX'),
          sox: byTicker('^SOX'),
          n225: byTicker('^N225'),
          vix: byTicker('^VIX'),
          vxn: byTicker('^VXN'),
          fearGreed: byTicker('FEAR_GREED'),
          move: byTicker('^MOVE'),
        },
      });
    }

    if (query.includes('GetPanicQuotes') || operationName === 'GetPanicQuotes') {
      const byTicker = (t: string) => mockMarketDailyQuotes.filter((q) => q.ticker === t);
      return HttpResponse.json({
        data: {
          vix: byTicker('^VIX'),
          vxn: byTicker('^VXN'),
          fearGreed: byTicker('FEAR_GREED'),
          move: byTicker('^MOVE'),
        },
      });
    }

    if (query.includes('listMacroYieldSnapshots') || operationName === 'ListMacroYieldSnapshots') {
      return HttpResponse.json({
        data: {
          listMacroYieldSnapshots: [mockMacroYieldSnapshot],
        },
      });
    }

    if (query.includes('getGlobalAssetByTicker')) {
      return HttpResponse.json({
        data: {
          getGlobalAssetByTicker: {
            ticker: body?.variables?.ticker ?? '0050',
            name: '元大台灣50',
            assetClass: 'CORE_EQUITY',
            underlyingIndex: '富時臺灣證券交易所臺灣50指數',
            distributionFrequency: '半年配',
            fundSizeTwd: 420000000000,
          },
        },
      });
    }

    if (query.includes('getDipBuyOpportunity')) {
      const ticker = body?.variables?.ticker ?? '0050';
      return HttpResponse.json({
        data: {
          getDipBuyOpportunity: {
            ticker,
            compositeScore: 78.5,
            dipScore: 78.5,
            starRating: '★★★★ 超跌區',
            winRateEstimate: '85.2%',
            historical1yWinRate: 85.2,
            recommendation: '分批加碼建立底倉',
            actionRecommendation: '分批加碼建立底倉',
            bollingerScore: 25.0,
            fibonacciScore: 20.0,
            maSupportScore: 18.5,
            panicScore: 15.0,
            calculatedAt: '2026-09-30T16:00:00Z',
          },
        },
      });
    }

    if (query.includes('getQuoteTimeSeries')) {
      const ticker = body?.variables?.ticker ?? '0050';
      // Generate 20 daily quotes for smooth kline rendering
      const quotes = Array.from({ length: 20 }).map((_, i) => {
        const day = 10 + i;
        const base = ticker.includes('0050') ? 190 : 25;
        const close = base + Math.sin(i / 2) * 5;
        return {
          ticker,
          tradeDate: `2026-09-${String(day).padStart(2, '0')}`,
          openPrice: close - 0.5,
          highPrice: close + 1.2,
          lowPrice: close - 1.0,
          closePrice: close,
          volumeShares: 15000000,
          volume: 15000000,
          ma20: base,
          ma60: base - 2,
          ma120: base - 5,
          ma240: base - 10,
          bbUpper: base + 6,
          bbMiddle: base,
          bbLower: base - 6,
        };
      });
      return HttpResponse.json({
        data: {
          getQuoteTimeSeries: quotes,
        },
      });
    }

    if (query.includes('getScoreByTicker')) {
      return HttpResponse.json({
        data: {
          getScoreByTicker: {
            ticker: '0050',
            evaluationDate: '2026-09-23',
            compositeScore: 92.4,
            classRank: 1,
            orthogonalStatus: 'SEED',
            rSquaredWithLeader: 1.0,
          },
        },
      });
    }

    if (query.includes('listMarketDailyQuotes') || operationName === 'ListMarketDailyQuotes') {
      return HttpResponse.json({
        data: {
          listMarketDailyQuotes: mockMarketDailyQuotes,
        },
      });
    }

    // Default empty data response for unhandled queries
    return HttpResponse.json({ data: {} });
  }),
];

// Re-export fixtures for test convenience
export * from './fixtures';
