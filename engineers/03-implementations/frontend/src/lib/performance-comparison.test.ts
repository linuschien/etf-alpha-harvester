import { describe, it, expect } from 'vitest';
import {
  buildPerformanceComparisonModel,
  buildSelectableUniverse,
  computeFetchRange,
  computeTargetStartDate,
  computeYAxisScale,
  filterActiveEtfs,
  resolveDataAnchorDate,
  type GlobalAssetOption,
} from './performance-comparison';

describe('performance-comparison pure calculation engine', () => {
  it('excludes delisted ETFs where both fundSizeTwd and netAssetValue are null, while keeping newly listed ETFs', () => {
    const assets: GlobalAssetOption[] = [
      { ticker: '0050', name: '元大台灣50', listingDate: '2003-06-30', netAssetValue: 190, fundSizeTwd: 4e11 },
      { ticker: '0054', name: '已下市ETF', listingDate: '2007-07-16', netAssetValue: null, fundSizeTwd: null },
      { ticker: '00999', name: '新上市ETF', listingDate: '2026-09-10', netAssetValue: 15.2, fundSizeTwd: null },
    ];

    const active = filterActiveEtfs(assets);
    expect(active.map((a) => a.ticker)).toEqual(['0050', '00999']);

    const universe = buildSelectableUniverse(assets);
    // 5 benchmarks first + 2 active ETFs sorted by ticker
    expect(universe.slice(0, 5).map((u) => u.ticker)).toEqual([
      '^TWII',
      '^GSPC',
      '^NDX',
      '^SOX',
      '^N225',
    ]);
    expect(universe.slice(5).map((u) => u.ticker)).toEqual(['0050', '00999']);
  });

  it('resolves data-anchored t_end from quotes or watermarks instead of wall-clock today', () => {
    const watermarks = [
      { feedName: 'TWSE_ETF_METADATA', latestRecordDate: '2026-11-01' },
      { feedName: 'TAIWAN_ETF_QUOTES', latestRecordDate: '2026-09-30T00:00:00' },
      { feedName: 'GLOBAL_BENCHMARKS', latestRecordDate: '2026-09-29T00:00:00' },
    ];

    expect(resolveDataAnchorDate(watermarks)).toBe('2026-09-30');
    expect(
      resolveDataAnchorDate(watermarks, {
        '0050': [{ tradeDate: '2026-09-28', closePrice: 100 }],
      })
    ).toBe('2026-09-28');
  });

  it('computes calendar target start dates and 2Y fetch ranges cleanly including month-end clamping', () => {
    expect(computeTargetStartDate('2026-09-30', '1M')).toBe('2026-08-30');
    expect(computeTargetStartDate('2026-03-31', '1M')).toBe('2026-02-28');
    expect(computeTargetStartDate('2026-09-30', '3M')).toBe('2026-06-30');
    expect(computeTargetStartDate('2026-09-30', '6M')).toBe('2026-03-30');
    expect(computeTargetStartDate('2026-09-30', '1Y')).toBe('2025-09-30');

    expect(computeFetchRange('2026-09-30')).toEqual({
      startDate: '2024-09-30',
      endDate: '2026-09-30',
    });
  });

  it('computes Formula B compound dividend reinvestment accurately (exact +21.00% on textbook 3-day case)', () => {
    const universeMap = new Map<string, GlobalAssetOption>([
      ['0050', { ticker: '0050', name: '元大台灣50', listingDate: '2003-06-30', netAssetValue: 110, fundSizeTwd: 4e11 }],
    ]);

    const model = buildPerformanceComparisonModel({
      selectedTickers: ['0050'],
      universeMap,
      quotesByTicker: {
        '0050': [
          { tradeDate: '2026-08-28', closePrice: 100 }, // t0 (<= 2026-08-30 Sunday)
          { tradeDate: '2026-09-01', closePrice: 110 }, // +10%
          { tradeDate: '2026-09-15', closePrice: 100 }, // Ex-div 10 -> S = 1 * (1 + 10/100) = 1.1 -> 1.1*100 = 110 (+10%)
          { tradeDate: '2026-09-30', closePrice: 110 }, // 1.1 * 110 = 121 -> +21.00% (compound!)
        ],
      },
      dividendsByTicker: {
        '0050': [
          { exDate: '2026-08-28', dividendPerShare: 5 }, // On t0: MUST be ignored!
          { exDate: '2026-09-15', dividendPerShare: 10 }, // Inside (t0, t_end]: reinvested!
        ],
      },
      anchorDate: '2026-09-30',
      window: '1M',
    });

    expect(model.originDate).toBe('2026-08-28');
    expect(model.dates).toEqual(['2026-08-28', '2026-09-01', '2026-09-15', '2026-09-30']);
    expect(model.series).toHaveLength(1);
    expect(model.series[0].points).toEqual([0, 10, 10, 21]);
    expect(model.series[0].finalReturnPct).toBe(21);
    expect(model.chipStates[0].finalReturnPct).toBe(21);
  });

  it('aligns cross-market holidays via Forward-Fill and individually looks back <= t0 for closed markets without shifting t0 for other assets', () => {
    const universeMap = new Map<string, GlobalAssetOption>([
      ['0050', { ticker: '0050', name: '元大台灣50', listingDate: '2003-06-30', netAssetValue: 110, fundSizeTwd: 4e11 }],
      ['^N225', { ticker: '^N225', name: '日本日經 225 指數', isBenchmark: true }],
    ]);

    // Target start for 1M from 2026-09-30 is 2026-08-30 (Sun).
    // 0050 traded on 2026-08-28 (Fri), while ^N225 was closed on 08-28 (last close on 08-27).
    // Common t0 MUST stay 2026-08-28; ^N225 looks back individually to 08-27 as P_t0.
    // On 09-17 Taiwan holiday (0050 closed, ^N225 open): 0050 forward-fills its prior return.
    const model = buildPerformanceComparisonModel({
      selectedTickers: ['0050', '^N225'],
      universeMap,
      quotesByTicker: {
        '0050': [
          { tradeDate: '2026-08-27', closePrice: 98 },
          { tradeDate: '2026-08-28', closePrice: 100 }, // t0 baseline = 100
          { tradeDate: '2026-09-10', closePrice: 105 }, // +5%
          // 09-17 holiday for Taiwan -> Forward-Fill +5%
          { tradeDate: '2026-09-30', closePrice: 110 }, // +10%
        ],
        '^N225': [
          { tradeDate: '2026-08-27', closePrice: 40000 }, // individual lookback <= t0 -> baseline = 40000
          // 08-28 holiday for Japan
          { tradeDate: '2026-09-10', closePrice: 42000 }, // +5%
          { tradeDate: '2026-09-17', closePrice: 44000 }, // +10%
          { tradeDate: '2026-09-30', closePrice: 46000 }, // +15%
        ],
      },
      dividendsByTicker: {},
      anchorDate: '2026-09-30',
      window: '1M',
    });

    expect(model.originDate).toBe('2026-08-28');
    expect(model.dates).toEqual(['2026-08-28', '2026-09-10', '2026-09-17', '2026-09-30']);

    const twSeries = model.series.find((s) => s.ticker === '0050')!;
    const jpSeries = model.series.find((s) => s.ticker === '^N225')!;

    expect(twSeries.baselinePrice).toBe(100);
    expect(twSeries.points).toEqual([0, 5, 5, 10]);

    expect(jpSeries.baselinePrice).toBe(40000);
    expect(jpSeries.points).toEqual([0, 5, 10, 15]);
  });

  it('marks newly listed ETF as insufficient_history in 6M and restores it in 1M', () => {
    const universeMap = new Map<string, GlobalAssetOption>([
      ['0050', { ticker: '0050', name: '元大台灣50', listingDate: '2003-06-30', netAssetValue: 190, fundSizeTwd: 4e11 }],
      ['00999', { ticker: '00999', name: '新上市ETF', listingDate: '2026-08-15', netAssetValue: 15, fundSizeTwd: 5e9 }],
    ]);

    const quotesByTicker = {
      '0050': [
        { tradeDate: '2026-03-30', closePrice: 150 },
        { tradeDate: '2026-08-28', closePrice: 180 },
        { tradeDate: '2026-09-30', closePrice: 198 },
      ],
      '00999': [
        { tradeDate: '2026-08-28', closePrice: 15 },
        { tradeDate: '2026-09-30', closePrice: 16.5 },
      ],
    };

    const model6M = buildPerformanceComparisonModel({
      selectedTickers: ['0050', '00999'],
      universeMap,
      quotesByTicker,
      dividendsByTicker: {},
      anchorDate: '2026-09-30',
      window: '6M',
    });

    expect(model6M.series.map((s) => s.ticker)).toEqual(['0050']);
    const chip999_6M = model6M.chipStates.find((c) => c.ticker === '00999')!;
    expect(chip999_6M.status).toBe('insufficient_history');
    expect(chip999_6M.reason).toContain('上市未滿 6M');

    const model1M = buildPerformanceComparisonModel({
      selectedTickers: ['0050', '00999'],
      universeMap,
      quotesByTicker,
      dividendsByTicker: {},
      anchorDate: '2026-09-30',
      window: '1M',
    });

    expect(model1M.series.map((s) => s.ticker)).toEqual(['0050', '00999']);
    const chip999_1M = model1M.chipStates.find((c) => c.ticker === '00999')!;
    expect(chip999_1M.status).toBe('ready');
    expect(chip999_1M.finalReturnPct).toBe(10);
  });

  it('computes nice Y-axis bounds that always include 0.00% baseline', () => {
    const scale = computeYAxisScale([
      {
        ticker: '0050',
        name: '元大台灣50',
        color: '#2563eb',
        isBenchmark: false,
        baselinePrice: 100,
        points: [0, 12.5, 28.4],
        finalReturnPct: 28.4,
      },
    ]);

    expect(scale.minY).toBeLessThanOrEqual(0);
    expect(scale.maxY).toBeGreaterThanOrEqual(28.4);
    expect(scale.ticks).toContain(0);
  });
});
