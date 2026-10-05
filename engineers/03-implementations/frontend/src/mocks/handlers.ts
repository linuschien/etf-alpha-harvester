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
      return HttpResponse.json({
        data: { listDividendAnnouncements: mockDividendAnnouncements },
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
      return HttpResponse.json({
        data: {
          getDipBuyOpportunity: {
            ticker: body?.variables?.ticker ?? '0050',
            dipScore: 78.5,
            starRating: '★★★★ 超跌區',
            historical1yWinRate: 85.2,
            actionRecommendation: '分批加碼建立底倉',
            calculatedAt: '2026-10-02T16:00:00Z',
          },
        },
      });
    }

    if (query.includes('getQuoteTimeSeries')) {
      return HttpResponse.json({
        data: {
          getQuoteTimeSeries: [
            { ticker: '0050', tradeDate: '2026-09-23', closePrice: 198.5, volume: 15000000 },
          ],
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

    if (query.includes('listMarketDailyQuotes')) {
      return HttpResponse.json({
        data: {
          listMarketDailyQuotes: [
            { ticker: '^TWII', tradeDate: '2026-09-23', closePrice: 22850.5, changePct: 0.8 },
          ],
        },
      });
    }

    // Default empty data response for unhandled queries
    return HttpResponse.json({ data: {} });
  }),
];

// Re-export fixtures for test convenience
export * from './fixtures';
