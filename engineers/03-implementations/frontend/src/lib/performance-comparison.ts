import { formatDate } from './date-utils';

export type CompareTimeWindow = '1M' | '3M' | '6M' | '1Y';

export const COMPARE_TIME_WINDOWS: CompareTimeWindow[] = ['1M', '3M', '6M', '1Y'];
export const DEFAULT_COMPARE_WINDOW: CompareTimeWindow = '6M';
export const MAX_COMPARE_ASSETS = 10;

export const COMPARE_COLOR_PALETTE: readonly string[] = [
  '#2563eb', // Blue
  '#9333ea', // Purple
  '#ea580c', // Orange
  '#059669', // Emerald
  '#e11d48', // Rose
  '#0891b2', // Cyan
  '#4f46e5', // Indigo
  '#d97706', // Amber
  '#db2777', // Pink
  '#0d9488', // Teal
] as const;

export interface GlobalAssetOption {
  ticker: string;
  name: string;
  listingDate?: string | null;
  netAssetValue?: number | null;
  fundSizeTwd?: number | null;
  underlyingIndex?: string | null;
  isBenchmark?: boolean;
}

export interface SimpleQuotePoint {
  tradeDate: string;
  closePrice: number;
}

export interface SimpleDividendEvent {
  exDate: string;
  dividendPerShare: number;
}

export interface WatermarkRecord {
  feedName?: string;
  latestRecordDate?: string | null;
  status?: string | null;
}

export const GLOBAL_BENCHMARK_OPTIONS: GlobalAssetOption[] = [
  { ticker: '^TWII', name: '台灣加權指數', isBenchmark: true },
  { ticker: '^GSPC', name: '美國標普 500 指數', isBenchmark: true },
  { ticker: '^NDX', name: '美國那斯達克 100 指數', isBenchmark: true },
  { ticker: '^SOX', name: '美國費城半導體指數', isBenchmark: true },
  { ticker: '^N225', name: '日本日經 225 指數', isBenchmark: true },
];

export type CompareAssetStatus = 'ready' | 'loading' | 'insufficient_history' | 'error';

export interface CompareSeriesItem {
  ticker: string;
  name: string;
  color: string;
  isBenchmark: boolean;
  baselinePrice: number;
  points: number[]; // aligned 1:1 with model.dates, in percentage (e.g. 18.65 for +18.65%)
  finalReturnPct: number;
}

export interface CompareChipState {
  ticker: string;
  name: string;
  color: string;
  isBenchmark: boolean;
  status: CompareAssetStatus;
  finalReturnPct: number | null;
  reason?: string;
}

export interface PerformanceComparisonModel {
  anchorDate: string;
  targetStartDate: string;
  originDate: string | null;
  endDate: string | null;
  dates: string[];
  series: CompareSeriesItem[];
  chipStates: CompareChipState[];
}

/**
 * Normalize an ISO date/datetime string to YYYY-MM-DD, returning null if invalid.
 */
export function normalizeIsoDate(raw?: string | null): string | null {
  if (!raw || typeof raw !== 'string') return null;
  const match = raw.trim().match(/^(\d{4}-\d{2}-\d{2})/);
  return match ? match[1] : null;
}

/**
 * One-time filter to exclude delisted ETFs where BOTH fundSizeTwd and netAssetValue are null/undefined.
 * Does NOT pre-filter by listingDate (insufficient listing age is handled uniformly via dimmed Chips).
 */
export function filterActiveEtfs(assets: GlobalAssetOption[]): GlobalAssetOption[] {
  if (!Array.isArray(assets)) return [];
  return assets.filter((asset) => {
    if (!asset || !asset.ticker) return false;
    if (asset.isBenchmark) return true;
    const hasFundSize = asset.fundSizeTwd !== null && asset.fundSizeTwd !== undefined;
    const hasNav = asset.netAssetValue !== null && asset.netAssetValue !== undefined;
    return hasFundSize || hasNav;
  });
}

/**
 * Build the complete searchable universe: 5 Global Benchmarks first, followed by active Taiwan ETFs sorted by ticker.
 */
export function buildSelectableUniverse(etfAssets: GlobalAssetOption[]): GlobalAssetOption[] {
  const activeEtfs = filterActiveEtfs(etfAssets)
    .filter((a) => !a.ticker.startsWith('^'))
    .sort((a, b) => a.ticker.localeCompare(b.ticker));

  const seen = new Set<string>();
  const result: GlobalAssetOption[] = [];

  for (const bm of GLOBAL_BENCHMARK_OPTIONS) {
    if (!seen.has(bm.ticker)) {
      seen.add(bm.ticker);
      result.push(bm);
    }
  }
  for (const etf of activeEtfs) {
    if (!seen.has(etf.ticker)) {
      seen.add(etf.ticker);
      result.push({ ...etf, isBenchmark: false });
    }
  }
  return result;
}

/**
 * Resolve the data-anchored baseline date t_end from watermarks (preferring quote/market feeds),
 * falling back to the latest tradeDate across loaded quotes, and finally today's date.
 */
export function resolveDataAnchorDate(
  watermarks?: WatermarkRecord[] | null,
  quotesByTicker?: Record<string, SimpleQuotePoint[] | undefined>,
  fallbackDate: Date = new Date()
): string {
  const quoteDates: string[] = [];
  if (quotesByTicker) {
    for (const list of Object.values(quotesByTicker)) {
      if (!Array.isArray(list)) continue;
      for (const q of list) {
        const d = normalizeIsoDate(q?.tradeDate);
        if (d) quoteDates.push(d);
      }
    }
  }
  if (quoteDates.length > 0) {
    quoteDates.sort();
    return quoteDates[quoteDates.length - 1];
  }

  if (Array.isArray(watermarks) && watermarks.length > 0) {
    const preferredFeeds = watermarks.filter((w) => {
      const name = String(w?.feedName || '').toUpperCase();
      return (
        name.includes('QUOTE') ||
        name.includes('MARKET') ||
        name.includes('BENCHMARK') ||
        name.includes('TWSE')
      ) && !name.includes('METADATA') && !name.includes('DCA') && !name.includes('TOP_LIST');
    });

    const candidates = (preferredFeeds.length > 0 ? preferredFeeds : watermarks)
      .map((w) => normalizeIsoDate(w?.latestRecordDate))
      .filter((d): d is string => Boolean(d));

    if (candidates.length > 0) {
      candidates.sort();
      return candidates[candidates.length - 1];
    }
  }

  return formatDate(fallbackDate);
}

/**
 * Subtract calendar months/years from YYYY-MM-DD cleanly (clamping end-of-month, e.g. Mar 31 - 1M -> Feb 28).
 */
export function computeTargetStartDate(
  anchorDate: string,
  window: CompareTimeWindow
): string {
  const normalized = normalizeIsoDate(anchorDate);
  if (!normalized) return anchorDate;

  const [yearStr, monthStr, dayStr] = normalized.split('-');
  const year = Number(yearStr);
  const month = Number(monthStr) - 1; // 0-indexed
  const day = Number(dayStr);

  const monthsToSubtract =
    window === '1M' ? 1 : window === '3M' ? 3 : window === '6M' ? 6 : 12;

  const targetMonthIndex = year * 12 + month - monthsToSubtract;
  const targetYear = Math.floor(targetMonthIndex / 12);
  const targetMonth = ((targetMonthIndex % 12) + 12) % 12;

  // Clamp day to the last valid day of targetMonth
  const daysInTargetMonth = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
  const clampedDay = Math.min(day, daysInTargetMonth);

  return `${targetYear}-${String(targetMonth + 1).padStart(2, '0')}-${String(clampedDay).padStart(2, '0')}`;
}

/**
 * Compute the GraphQL query fetch range [startDate, endDate] anchored at t_end
 * with a 2-year lookback buffer so 1Y always has a valid trading day <= targetStartDate
 * even when local dev snapshots lag behind wall-clock time or span long holidays.
 */
export function computeFetchRange(anchorDate: string): { startDate: string; endDate: string } {
  const normalized = normalizeIsoDate(anchorDate) || formatDate(new Date());
  const oneYearAgo = computeTargetStartDate(normalized, '1Y');
  const twoYearsAgo = computeTargetStartDate(oneYearAgo, '1Y');
  return { startDate: twoYearsAgo, endDate: normalized };
}

/**
 * Assign deterministic colors from the 10-color palette based on selection slot order.
 */
export function getColorForIndex(index: number): string {
  return COMPARE_COLOR_PALETTE[index % COMPARE_COLOR_PALETTE.length];
}

export interface BuildComparisonParams {
  selectedTickers: string[];
  universeMap: Map<string, GlobalAssetOption>;
  quotesByTicker: Record<string, SimpleQuotePoint[] | undefined>;
  dividendsByTicker: Record<string, SimpleDividendEvent[] | undefined>;
  loadingByTicker?: Record<string, boolean>;
  errorByTicker?: Record<string, boolean>;
  anchorDate: string;
  window: CompareTimeWindow;
}

/**
 * Core pure engine for Multi-Asset Normalized Dividend-Reinvested Total Return Comparison:
 * - Data-anchored at t_end (anchorDate)
 * - Common origin t_0 = latest trading date in union of eligible tickers that is <= targetStartDate
 * - If an individual market is closed on t_0, ONLY that ticker looks back to its latest close <= t_0
 * - Formula B (Compound Dividend Reinvestment): S_{t0} = 1.0, on ex-date S_t = S_{t-1} * (1 + D_t / P_t),
 *   R_t(%) = ((S_t * P_t - P_{t0}) / P_{t0}) * 100
 * - Cross-market alignment by exact calendar trade date + Forward-Fill on market holidays
 */
export function buildPerformanceComparisonModel({
  selectedTickers,
  universeMap,
  quotesByTicker,
  dividendsByTicker,
  loadingByTicker = {},
  errorByTicker = {},
  anchorDate,
  window,
}: BuildComparisonParams): PerformanceComparisonModel {
  const targetStartDate = computeTargetStartDate(anchorDate, window);

  interface PreparedTicker {
    ticker: string;
    name: string;
    color: string;
    isBenchmark: boolean;
    sortedQuotes: Array<{ date: string; close: number }>;
    quoteMap: Map<string, number>;
    dividends: Array<{ exDate: string; amount: number }>;
  }

  const preparedMap = new Map<string, PreparedTicker>();
  const statusMap = new Map<string, { status: CompareAssetStatus; reason?: string }>();

  selectedTickers.forEach((ticker, idx) => {
    const meta = universeMap.get(ticker);
    const name = meta?.name || ticker;
    const isBenchmark = Boolean(meta?.isBenchmark || ticker.startsWith('^'));
    const color = getColorForIndex(idx);

    const listingDate = normalizeIsoDate(meta?.listingDate);
    if (!isBenchmark && listingDate && listingDate > targetStartDate) {
      statusMap.set(ticker, {
        status: 'insufficient_history',
        reason: `上市未滿 ${window}，暫不顯示`,
      });
      return;
    }

    if (loadingByTicker[ticker]) {
      statusMap.set(ticker, { status: 'loading' });
      return;
    }

    if (errorByTicker[ticker]) {
      statusMap.set(ticker, {
        status: 'error',
        reason: '報價載入失敗',
      });
      return;
    }

    const rawQuotes = quotesByTicker[ticker];
    if (!rawQuotes) {
      statusMap.set(ticker, { status: 'loading' });
      return;
    }

    // Deduplicate and sort valid positive close prices <= anchorDate
    const quoteMap = new Map<string, number>();
    for (const q of rawQuotes) {
      const d = normalizeIsoDate(q?.tradeDate);
      const close = Number(q?.closePrice);
      if (d && d <= anchorDate && Number.isFinite(close) && close > 0) {
        quoteMap.set(d, close);
      }
    }

    const sortedQuotes = Array.from(quoteMap.entries())
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([date, close]) => ({ date, close }));

    if (sortedQuotes.length === 0) {
      statusMap.set(ticker, {
        status: 'insufficient_history',
        reason: `上市未滿 ${window}，暫不顯示`,
      });
      return;
    }

    // Must have at least one valid quote <= targetStartDate to satisfy the window baseline
    const earliestDate = sortedQuotes[0].date;
    if (earliestDate > targetStartDate) {
      statusMap.set(ticker, {
        status: 'insufficient_history',
        reason: `上市未滿 ${window}，暫不顯示`,
      });
      return;
    }

    // Prepare sorted dividends (already on post-split scale matching getQuoteTimeSeries)
    const rawDivs = dividendsByTicker[ticker] || [];
    const divByDate = new Map<string, number>();
    if (!isBenchmark) {
      for (const div of rawDivs) {
        const exDate = normalizeIsoDate(div?.exDate);
        const amount = Number(div?.dividendPerShare);
        if (exDate && exDate <= anchorDate && Number.isFinite(amount) && amount > 0) {
          divByDate.set(exDate, (divByDate.get(exDate) ?? 0) + amount);
        }
      }
    }
    const dividends = Array.from(divByDate.entries())
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([exDate, amount]) => ({ exDate, amount }));

    preparedMap.set(ticker, {
      ticker,
      name,
      color,
      isBenchmark,
      sortedQuotes,
      quoteMap,
      dividends,
    });
    statusMap.set(ticker, { status: 'ready' });
  });

  // Collect union of all trading dates across ready tickers
  const unionDateSet = new Set<string>();
  for (const item of preparedMap.values()) {
    for (const q of item.sortedQuotes) {
      unionDateSet.add(q.date);
    }
  }
  const unionDates = Array.from(unionDateSet).sort();

  // Determine common origin date t_0: latest date in unionDates that is <= targetStartDate
  let originDate: string | null = null;
  for (let i = unionDates.length - 1; i >= 0; i--) {
    if (unionDates[i] <= targetStartDate) {
      originDate = unionDates[i];
      break;
    }
  }

  const dates = originDate
    ? unionDates.filter((d) => d >= originDate! && d <= anchorDate)
    : [];

  const series: CompareSeriesItem[] = [];
  const finalReturnByTicker = new Map<string, number>();

  if (originDate && dates.length > 0) {
    for (const ticker of selectedTickers) {
      const item = preparedMap.get(ticker);
      if (!item) continue;

      // Baseline price P_{t0}: latest valid close <= originDate for THIS ticker individually
      let baselinePrice = 0;
      for (let i = item.sortedQuotes.length - 1; i >= 0; i--) {
        if (item.sortedQuotes[i].date <= originDate) {
          baselinePrice = item.sortedQuotes[i].close;
          break;
        }
      }

      if (baselinePrice <= 0) {
        statusMap.set(ticker, {
          status: 'insufficient_history',
          reason: `上市未滿 ${window}，暫不顯示`,
        });
        continue;
      }

      // Compute Formula B (Compound Dividend Reinvestment) cumulative total return series
      let cumulativeShares = 1.0;
      let lastReturnPct = 0.0;
      let divCursor = 0;

      // Skip dividends on or before originDate (only dividends with exDate > originDate count)
      while (divCursor < item.dividends.length && item.dividends[divCursor].exDate <= originDate) {
        divCursor++;
      }

      const points: number[] = [];

      for (let i = 0; i < dates.length; i++) {
        const d = dates[i];
        if (i === 0) {
          // Exact 0.00% origin alignment at t_0
          points.push(0);
          lastReturnPct = 0;
          continue;
        }

        const closeOnDate = item.quoteMap.get(d);
        if (closeOnDate !== undefined && closeOnDate > 0) {
          // Reinvest any dividends whose exDate fell in (prevProcessed, d]
          while (divCursor < item.dividends.length && item.dividends[divCursor].exDate <= d) {
            const divAmt = item.dividends[divCursor].amount;
            cumulativeShares = cumulativeShares * (1 + divAmt / closeOnDate);
            divCursor++;
          }
          const totalReturnPct =
            ((cumulativeShares * closeOnDate - baselinePrice) / baselinePrice) * 100;
          lastReturnPct = Number(totalReturnPct.toFixed(4));
          points.push(lastReturnPct);
        } else {
          // Market holiday for this ticker: Forward-Fill previous valid cumulative return
          points.push(lastReturnPct);
        }
      }

      const finalReturnPct = points.length > 0 ? points[points.length - 1] : 0;
      finalReturnByTicker.set(ticker, finalReturnPct);

      series.push({
        ticker: item.ticker,
        name: item.name,
        color: item.color,
        isBenchmark: item.isBenchmark,
        baselinePrice,
        points,
        finalReturnPct,
      });
    }
  }

  const chipStates: CompareChipState[] = selectedTickers.map((ticker, idx) => {
    const meta = universeMap.get(ticker);
    const st = statusMap.get(ticker) || { status: 'loading' as CompareAssetStatus };
    const finalReturnPct = finalReturnByTicker.has(ticker)
      ? finalReturnByTicker.get(ticker)!
      : null;

    return {
      ticker,
      name: meta?.name || ticker,
      color: getColorForIndex(idx),
      isBenchmark: Boolean(meta?.isBenchmark || ticker.startsWith('^')),
      status: st.status,
      finalReturnPct,
      reason: st.reason,
    };
  });

  return {
    anchorDate,
    targetStartDate,
    originDate,
    endDate: dates.length > 0 ? dates[dates.length - 1] : null,
    dates,
    series,
    chipStates,
  };
}

/**
 * Compute nice Y-axis bounds and ticks for percentage series (always including 0.00% baseline).
 */
export function computeYAxisScale(series: CompareSeriesItem[]): {
  minY: number;
  maxY: number;
  ticks: number[];
} {
  let minVal = 0;
  let maxVal = 0;

  for (const s of series) {
    for (const p of s.points) {
      if (Number.isFinite(p)) {
        if (p < minVal) minVal = p;
        if (p > maxVal) maxVal = p;
      }
    }
  }

  const rawSpan = Math.max(maxVal - minVal, 4);
  const pad = rawSpan * 0.1;
  const paddedMin = minVal - pad;
  const paddedMax = maxVal + pad;

  const roughStep = (paddedMax - paddedMin) / 6;
  const niceSteps = [0.5, 1, 2, 2.5, 5, 10, 15, 20, 25, 50, 100];
  let step = niceSteps[niceSteps.length - 1];
  for (const candidate of niceSteps) {
    if (candidate >= roughStep) {
      step = candidate;
      break;
    }
  }

  const minY = Math.floor(paddedMin / step) * step;
  const maxY = Math.ceil(paddedMax / step) * step;

  const ticks: number[] = [];
  for (let v = minY; v <= maxY + step * 0.001; v += step) {
    ticks.push(Number(v.toFixed(2)));
  }

  return { minY, maxY, ticks };
}
