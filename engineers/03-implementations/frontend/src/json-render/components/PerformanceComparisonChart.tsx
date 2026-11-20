import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useQueries } from '@tanstack/react-query';
import { useStateStore } from '@json-render/react';
import { Check, LineChart, Search, Trash2, X } from 'lucide-react';
import { api } from '@/lib/api-client';
import { useListGlobalAssets } from '@/hooks/use-list-global-assets';
import type { MarketDailyQuote } from '@/hooks/use-get-quote-time-series';
import type { DividendAnnouncement } from '@/hooks/use-list-dividend-announcements';
import SegmentedRadio from './SegmentedRadio';
import {
  buildPerformanceComparisonModel,
  buildSelectableUniverse,
  COMPARE_TIME_WINDOWS,
  computeFetchRange,
  computeYAxisScale,
  DEFAULT_COMPARE_WINDOW,
  MAX_COMPARE_ASSETS,
  resolveDataAnchorDate,
  type CompareTimeWindow,
  type GlobalAssetOption,
  type SimpleDividendEvent,
  type SimpleQuotePoint,
  type WatermarkRecord,
} from '@/lib/performance-comparison';

export interface PerformanceComparisonChartProps {
  element?: {
    props?: {
      id?: string;
      label?: string;
      className?: string;
    };
  };
  props?: {
    id?: string;
    label?: string;
    className?: string;
  };
}

const CHART_HEIGHT = 540; // Extended vertical scale >= 520px for up to 10 lines
const PADDING_LEFT = 66;
const PADDING_RIGHT = 24;
const PADDING_TOP = 26;
const PADDING_BOTTOM = 42;

function formatReturnPct(val: number): string {
  const rounded = Number(val.toFixed(2));
  if (rounded > 0) return `+${rounded.toFixed(2)}%`;
  if (rounded < 0) return `${rounded.toFixed(2)}%`;
  return '0.00%';
}

function getReturnColorClass(val: number): string {
  if (val > 0) return 'text-emerald-600 dark:text-emerald-400';
  if (val < 0) return 'text-rose-600 dark:text-rose-400';
  return 'text-muted-foreground';
}

export default function PerformanceComparisonChart({
  element,
  props: directProps,
}: PerformanceComparisonChartProps) {
  const props = element?.props ?? directProps ?? {};

  let store: any = null;
  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  // Subscribe to store updates for in-session state persistence across tab switches
  const [, setStoreTick] = useState(0);
  useEffect(() => {
    if (!store?.subscribe) return;
    const unsub = store.subscribe(() => {
      setStoreTick((t) => t + 1);
    });
    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [store]);

  // Local fallback state when rendered standalone without store
  const [localTickers, setLocalTickers] = useState<string[]>([]);
  const [localWindow, setLocalWindow] = useState<CompareTimeWindow>(DEFAULT_COMPARE_WINDOW);

  const selectedTickers: string[] = useMemo(() => {
    const fromStore = store?.get?.('/filters/performance-compare-tickers');
    if (Array.isArray(fromStore)) return fromStore;
    return localTickers;
  }, [store, localTickers, store?.get?.('/filters/performance-compare-tickers')]);

  const selectedWindow: CompareTimeWindow = useMemo(() => {
    const raw = store?.get?.('/filters/performance-compare-window');
    if (raw === '1M' || raw === '3M' || raw === '6M' || raw === '1Y') return raw;
    return localWindow;
  }, [store, localWindow, store?.get?.('/filters/performance-compare-window')]);

  const setSelectedTickers = (next: string[]) => {
    const capped = next.slice(0, MAX_COMPARE_ASSETS);
    setLocalTickers(capped);
    if (store?.set) {
      store.set('/filters/performance-compare-tickers', capped);
    }
  };

  const handleWindowChange = (nextWindow: string) => {
    const valid: CompareTimeWindow =
      nextWindow === '1M' || nextWindow === '3M' || nextWindow === '6M' || nextWindow === '1Y'
        ? nextWindow
        : DEFAULT_COMPARE_WINDOW;
    setLocalWindow(valid);
    if (store?.set) {
      store.set('/filters/performance-compare-window', valid);
    }
  };

  // Search combobox state
  const [searchQuery, setSearchQuery] = useState('');
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const comboboxRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (comboboxRef.current && !comboboxRef.current.contains(event.target as Node)) {
        setDropdownOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Responsive SVG width
  const chartContainerRef = useRef<HTMLDivElement>(null);
  const [chartWidth, setChartWidth] = useState(900);
  useEffect(() => {
    const el = chartContainerRef.current;
    if (!el) return;
    const updateWidth = () => {
      const w = el.clientWidth;
      if (w > 300) setChartWidth(w);
    };
    updateWidth();
    if (typeof ResizeObserver !== 'undefined') {
      const ro = new ResizeObserver(updateWidth);
      ro.observe(el);
      return () => ro.disconnect();
    }
  }, []);

  // Hover state for crosshair + DESC-sorted tooltip
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);

  // Fetch active global assets metadata (once per session, cached 30m)
  const { data: fetchedGlobalAssets, isLoading: isAssetsLoading } = useListGlobalAssets({
    enabled: true,
  });

  // Allow store injection for deterministic testing as well as live API data
  const storeGlobalAssets: GlobalAssetOption[] | undefined =
    store?.get?.('/data/listGlobalAssets');
  const rawGlobalAssets =
    Array.isArray(storeGlobalAssets) && storeGlobalAssets.length > 0
      ? storeGlobalAssets
      : fetchedGlobalAssets ?? [];

  const universe = useMemo(
    () => buildSelectableUniverse(rawGlobalAssets),
    [rawGlobalAssets]
  );

  const universeMap = useMemo(() => {
    const map = new Map<string, GlobalAssetOption>();
    for (const item of universe) {
      map.set(item.ticker, item);
    }
    return map;
  }, [universe]);

  // Watermarks from store for data-anchored t_end baseline
  const watermarks: WatermarkRecord[] = store?.get?.('/data/listDataFeedWatermarks') || [];

  // Determine fetch range anchored at watermark t_end (2-year lookback buffer)
  const watermarkAnchorDate = useMemo(
    () => resolveDataAnchorDate(watermarks),
    [watermarks]
  );
  const fetchRange = useMemo(
    () => computeFetchRange(watermarkAnchorDate),
    [watermarkAnchorDate]
  );

  // Parallel on-demand quote queries for selected tickers
  const quoteQueries = useQueries({
    queries: selectedTickers.map((ticker) => ({
      queryKey: ['compareQuoteTimeSeries', ticker, fetchRange.startDate, fetchRange.endDate],
      staleTime: 1000 * 60 * 5,
      queryFn: () =>
        api
          .graphql<{ getQuoteTimeSeries: MarketDailyQuote[] }>(
            `query GetCompareQuoteTimeSeries($ticker: String!, $startDate: String!, $endDate: String!) {
              getQuoteTimeSeries(ticker: $ticker, startDate: $startDate, endDate: $endDate) {
                ticker
                tradeDate
                closePrice
              }
            }`,
            {
              ticker,
              startDate: fetchRange.startDate,
              endDate: fetchRange.endDate,
            }
          )
          .then((res) => res.getQuoteTimeSeries ?? []),
    })),
  });

  // Parallel on-demand dividend queries for non-benchmark ETF tickers
  const dividendQueries = useQueries({
    queries: selectedTickers.map((ticker) => {
      const isBenchmark = ticker.startsWith('^') || Boolean(universeMap.get(ticker)?.isBenchmark);
      return {
        queryKey: ['compareDividends', ticker],
        enabled: !isBenchmark,
        staleTime: 1000 * 60 * 30,
        queryFn: () =>
          api
            .graphql<{ listDividendAnnouncements: DividendAnnouncement[] }>(
              `query ListCompareDividends($filter: DividendAnnouncementFilterInput) {
                listDividendAnnouncements(filter: $filter) {
                  ticker
                  exDate
                  dividendPerShare
                }
              }`,
              { filter: { ticker } }
            )
            .then((res) => res.listDividendAnnouncements ?? []),
      };
    }),
  });

  // Assemble maps for pure calculation model
  const { quotesByTicker, dividendsByTicker, loadingByTicker, errorByTicker } = useMemo(() => {
    const qMap: Record<string, SimpleQuotePoint[] | undefined> = {};
    const dMap: Record<string, SimpleDividendEvent[] | undefined> = {};
    const lMap: Record<string, boolean> = {};
    const eMap: Record<string, boolean> = {};

    selectedTickers.forEach((ticker, idx) => {
      const qq = quoteQueries[idx];
      const dq = dividendQueries[idx];
      const isBenchmark = ticker.startsWith('^') || Boolean(universeMap.get(ticker)?.isBenchmark);

      qMap[ticker] = qq?.data;
      dMap[ticker] = isBenchmark ? [] : dq?.data;
      lMap[ticker] = Boolean(qq?.isLoading || (!isBenchmark && dq?.isLoading));
      eMap[ticker] = Boolean(qq?.isError);
    });

    return {
      quotesByTicker: qMap,
      dividendsByTicker: dMap,
      loadingByTicker: lMap,
      errorByTicker: eMap,
    };
  }, [selectedTickers, quoteQueries, dividendQueries, universeMap]);

  // Resolve effective data anchor date t_end (preferring latest loaded quote date, falling back to watermark)
  const effectiveAnchorDate = useMemo(
    () => resolveDataAnchorDate(watermarks, quotesByTicker),
    [watermarks, quotesByTicker]
  );

  // Build normalized dividend-reinvested performance comparison model
  const comparisonModel = useMemo(
    () =>
      buildPerformanceComparisonModel({
        selectedTickers,
        universeMap,
        quotesByTicker,
        dividendsByTicker,
        loadingByTicker,
        errorByTicker,
        anchorDate: effectiveAnchorDate,
        window: selectedWindow,
      }),
    [
      selectedTickers,
      universeMap,
      quotesByTicker,
      dividendsByTicker,
      loadingByTicker,
      errorByTicker,
      effectiveAnchorDate,
      selectedWindow,
    ]
  );

  // Reset hover index when date axis changes
  useEffect(() => {
    setHoverIndex(null);
  }, [comparisonModel.dates.length, selectedWindow]);

  // Search filtering (does NOT pre-filter listingDate — unified dimmed Chip behavior)
  const filteredOptions = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return universe.slice(0, 60);
    return universe
      .filter(
        (opt) =>
          opt.ticker.toLowerCase().includes(q) ||
          opt.name.toLowerCase().includes(q)
      )
      .slice(0, 60);
  }, [universe, searchQuery]);

  const isAtCapacity = selectedTickers.length >= MAX_COMPARE_ASSETS;

  const handleAddTicker = (ticker: string) => {
    if (selectedTickers.includes(ticker) || isAtCapacity) return;
    setSelectedTickers([...selectedTickers, ticker]);
    setSearchQuery('');
    setDropdownOpen(false);
  };

  const handleRemoveTicker = (ticker: string) => {
    setSelectedTickers(selectedTickers.filter((t) => t !== ticker));
  };

  const handleClearAll = () => {
    setSelectedTickers([]);
    setHoverIndex(null);
  };

  // Y-axis scaling & coordinates
  const yAxis = useMemo(
    () => computeYAxisScale(comparisonModel.series),
    [comparisonModel.series]
  );

  const plotWidth = Math.max(240, chartWidth - PADDING_LEFT - PADDING_RIGHT);
  const plotHeight = CHART_HEIGHT - PADDING_TOP - PADDING_BOTTOM;
  const dateCount = comparisonModel.dates.length;

  const scaleX = (idx: number) => {
    if (dateCount <= 1) return PADDING_LEFT;
    return PADDING_LEFT + (idx / (dateCount - 1)) * plotWidth;
  };

  const scaleY = (val: number) => {
    const span = Math.max(0.0001, yAxis.maxY - yAxis.minY);
    return PADDING_TOP + ((yAxis.maxY - val) / span) * plotHeight;
  };

  const zeroY = scaleY(0);

  // Decimated X-axis date ticks (up to 6 evenly spaced ISO labels)
  const xTickIndices = useMemo(() => {
    if (dateCount === 0) return [];
    if (dateCount <= 6) return Array.from({ length: dateCount }, (_, i) => i);
    const steps = 5;
    const indices: number[] = [];
    for (let i = 0; i <= steps; i++) {
      indices.push(Math.round((i / steps) * (dateCount - 1)));
    }
    return Array.from(new Set(indices));
  }, [dateCount]);

  // Handle mouse interaction on SVG overlay
  const handleSvgMouseMove = (e: React.MouseEvent<SVGRectElement>) => {
    if (dateCount === 0) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const relX = Math.max(0, Math.min(rect.width, e.clientX - rect.left));
    const ratio = rect.width > 0 ? relX / rect.width : 0;
    const idx = Math.round(ratio * Math.max(0, dateCount - 1));
    setHoverIndex(Math.max(0, Math.min(dateCount - 1, idx)));
  };

  // Tooltip items sorted DESC by cumulative return on hovered date
  const hoveredTooltip = useMemo(() => {
    if (hoverIndex === null || hoverIndex < 0 || hoverIndex >= dateCount) return null;
    const date = comparisonModel.dates[hoverIndex];
    const rows = comparisonModel.series
      .map((s) => ({
        ticker: s.ticker,
        name: s.name,
        color: s.color,
        value: s.points[hoverIndex] ?? 0,
      }))
      .sort((a, b) => b.value - a.value);
    return {
      date,
      x: scaleX(hoverIndex),
      rows,
    };
  }, [hoverIndex, dateCount, comparisonModel.dates, comparisonModel.series, plotWidth]);

  const anyLoading = comparisonModel.chipStates.some((c) => c.status === 'loading');
  const hasVisibleSeries = comparisonModel.series.length > 0 && dateCount > 0;
  const allInsufficient =
    selectedTickers.length > 0 &&
    !anyLoading &&
    !hasVisibleSeries &&
    comparisonModel.chipStates.every((c) => c.status === 'insufficient_history');

  return (
    <div
      id={props.id || 'performance-comparison-container'}
      className={`w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-5 ${props.className ?? ''}`}
    >
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/60 pb-4">
        <div className="flex items-center gap-2.5">
          <LineChart className="w-5 h-5 text-primary shrink-0" aria-hidden="true" />
          <div>
            <h2 className="text-lg font-bold tracking-tight text-foreground">
              多標的同起點含息績效比較
            </h2>
            <p className="text-xs text-muted-foreground mt-0.5">
              所有標的於區間起始交易日 (t₀) 對齊為 0.00%，一律採含息總報酬（除息日配息再投入複利）；上市時間不滿足視窗者呈灰階標籤暫不顯示。
            </p>
          </div>
        </div>
        {comparisonModel.originDate && comparisonModel.endDate && hasVisibleSeries && (
          <div className="text-xs font-mono text-muted-foreground bg-muted/50 px-3 py-1.5 rounded-lg border border-border/60">
            比較區間：{comparisonModel.originDate} ～ {comparisonModel.endDate}
          </div>
        )}
      </div>

      {/* Control Toolbar: Search Multi-Select + Time Window Radio + Clear All */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        {/* Searchable Multi-Select Combobox */}
        <div ref={comboboxRef} className="relative flex-1 min-w-[280px] max-w-xl">
          <div className="relative flex items-center">
            <Search
              className="w-4 h-4 text-muted-foreground absolute left-3 pointer-events-none"
              aria-hidden="true"
            />
            <input
              id="performance-compare-asset-selector"
              type="text"
              role="combobox"
              aria-expanded={dropdownOpen}
              aria-label="新增比較標的"
              disabled={isAtCapacity}
              value={searchQuery}
              onFocus={() => setDropdownOpen(true)}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setDropdownOpen(true);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Escape') {
                  setDropdownOpen(false);
                }
              }}
              placeholder={
                isAtCapacity
                  ? `已達上限 (${MAX_COMPARE_ASSETS} / ${MAX_COMPARE_ASSETS} 檔)`
                  : `搜尋 ETF 代碼/名稱或全球基準指數 (已選 ${selectedTickers.length} / ${MAX_COMPARE_ASSETS} 檔)...`
              }
              className="w-full pl-9 pr-20 py-2 text-sm rounded-lg border border-input bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-ring disabled:opacity-60 disabled:cursor-not-allowed"
            />
            <span className="absolute right-3 text-xs font-mono font-semibold text-muted-foreground">
              {selectedTickers.length}/{MAX_COMPARE_ASSETS}
            </span>
          </div>

          {dropdownOpen && !isAtCapacity && (
            <div
              role="listbox"
              aria-label="可選比較標的清單"
              className="absolute z-40 mt-1.5 w-full max-h-72 overflow-y-auto rounded-lg border border-border bg-popover text-popover-foreground shadow-lg"
            >
              {isAssetsLoading && universe.length <= 5 ? (
                <div className="px-4 py-3 text-xs text-muted-foreground">
                  載入全市場 ETF 清單中...
                </div>
              ) : filteredOptions.length === 0 ? (
                <div className="px-4 py-3 text-xs text-muted-foreground">
                  查無符合「{searchQuery}」的有效掛牌標的或基準指數
                </div>
              ) : (
                filteredOptions.map((opt) => {
                  const isSelected = selectedTickers.includes(opt.ticker);
                  return (
                    <button
                      key={opt.ticker}
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      disabled={isSelected}
                      onClick={() => handleAddTicker(opt.ticker)}
                      className={`w-full px-3.5 py-2 text-left text-sm flex items-center justify-between gap-2 transition-colors ${
                        isSelected
                          ? 'bg-muted/60 text-muted-foreground cursor-default'
                          : 'hover:bg-accent hover:text-accent-foreground cursor-pointer'
                      }`}
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <span
                          className={`text-[10px] font-semibold px-1.5 py-0.5 rounded border shrink-0 ${
                            opt.isBenchmark
                              ? 'bg-amber-500/10 text-amber-700 dark:text-amber-300 border-amber-500/30'
                              : 'bg-primary/10 text-primary border-primary/20'
                          }`}
                        >
                          {opt.isBenchmark ? '基準指數' : 'ETF'}
                        </span>
                        <span className="font-mono font-bold shrink-0">{opt.ticker}</span>
                        <span className="truncate text-muted-foreground">{opt.name}</span>
                      </div>
                      {isSelected && (
                        <Check className="w-4 h-4 text-primary shrink-0" aria-hidden="true" />
                      )}
                    </button>
                  );
                })
              )}
            </div>
          )}
        </div>

        {/* Time Window Segmented Radio + Clear All Button */}
        <div className="flex flex-wrap items-center gap-3">
          <SegmentedRadio
            props={{
              id: 'performance-compare-window',
              label: '時間視窗',
              options: COMPARE_TIME_WINDOWS,
              value: selectedWindow,
            }}
            bindings={{ value: '/filters/performance-compare-window' }}
            emit={(_ev, val) => handleWindowChange(val)}
          />

          <button
            id="performance-compare-clear-btn"
            type="button"
            disabled={selectedTickers.length === 0}
            onClick={handleClearAll}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium rounded-lg border border-border bg-secondary text-secondary-foreground hover:bg-secondary/80 transition-colors cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed"
          >
            <Trash2 className="w-3.5 h-3.5" aria-hidden="true" />
            <span>清除全部</span>
          </button>
        </div>
      </div>

      {/* Selected Asset Colored Chips (with Final Return or Dimmed Insufficient-History Badge) */}
      {comparisonModel.chipStates.length > 0 && (
        <div
          className="flex flex-wrap items-center gap-2 pt-1"
          aria-label="已選比較標的"
        >
          {comparisonModel.chipStates.map((chip) => {
            const isDimmed = chip.status === 'insufficient_history';
            const isError = chip.status === 'error';
            const isLoading = chip.status === 'loading';

            return (
              <div
                key={chip.ticker}
                data-testid={`compare-chip-${chip.ticker}`}
                data-status={chip.status}
                className={`inline-flex items-center gap-2 pl-2.5 pr-1.5 py-1 rounded-full border text-xs transition-colors ${
                  isDimmed
                    ? 'bg-muted/40 border-border/70 text-muted-foreground opacity-65 grayscale'
                    : isError
                    ? 'bg-destructive/10 border-destructive/30 text-destructive'
                    : 'bg-background border-border text-foreground shadow-2xs'
                }`}
              >
                <span
                  className="w-2.5 h-2.5 rounded-full shrink-0"
                  style={{ backgroundColor: isDimmed ? '#9ca3af' : chip.color }}
                  aria-hidden="true"
                />
                <span className="font-mono font-bold">{chip.ticker}</span>
                <span className="max-w-[130px] truncate text-muted-foreground">
                  {chip.name}
                </span>

                {chip.status === 'ready' && chip.finalReturnPct !== null && (
                  <span
                    className={`font-mono font-bold ${getReturnColorClass(chip.finalReturnPct)}`}
                  >
                    {formatReturnPct(chip.finalReturnPct)}
                  </span>
                )}

                {isDimmed && (
                  <span className="text-[11px] font-medium px-1.5 py-0.5 rounded bg-muted text-muted-foreground">
                    {chip.reason || `上市未滿 ${selectedWindow}，暫不顯示`}
                  </span>
                )}

                {isLoading && (
                  <span className="text-[11px] text-muted-foreground animate-pulse">
                    載入中...
                  </span>
                )}

                {isError && (
                  <span className="text-[11px] font-medium">
                    {chip.reason || '載入失敗'}
                  </span>
                )}

                <button
                  type="button"
                  aria-label={`移除 ${chip.ticker}`}
                  onClick={() => handleRemoveTicker(chip.ticker)}
                  className="p-0.5 rounded-full hover:bg-muted text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
                >
                  <X className="w-3.5 h-3.5" aria-hidden="true" />
                </button>
              </div>
            );
          })}
        </div>
      )}

      {/* Main Extended-Height Pure SVG Line Chart Area (No Summary Table Below) */}
      <div
        ref={chartContainerRef}
        className="relative w-full rounded-xl border border-border/70 bg-background/50 p-3"
        style={{ minHeight: `${CHART_HEIGHT + 24}px` }}
      >
        {selectedTickers.length === 0 ? (
          <div
            className="w-full flex flex-col items-center justify-center text-center gap-3 px-6"
            style={{ height: `${CHART_HEIGHT}px` }}
          >
            <div className="w-12 h-12 rounded-full bg-primary/10 text-primary flex items-center justify-center">
              <LineChart className="w-6 h-6" aria-hidden="true" />
            </div>
            <div className="space-y-1 max-w-md">
              <div className="text-sm font-semibold text-foreground">
                尚未挑選任何比較標的
              </div>
              <p className="text-xs text-muted-foreground leading-relaxed">
                請從上方搜尋框挑選最多 {MAX_COMPARE_ASSETS} 檔台股 ETF 或全球基準指數，系統將於區間起點對齊為 0.00%，繪製含息累積報酬走勢。
              </p>
            </div>
          </div>
        ) : allInsufficient ? (
          <div
            className="w-full flex flex-col items-center justify-center text-center gap-2 px-6"
            style={{ height: `${CHART_HEIGHT}px` }}
          >
            <div className="text-sm font-semibold text-foreground">
              所選標的上市時間皆未滿此區間（{selectedWindow}）
            </div>
            <p className="text-xs text-muted-foreground max-w-md">
              請切換較短的時間視窗（例如 1M 或 3M），或改選其他上市年資較長的標的進行比較。
            </p>
          </div>
        ) : !hasVisibleSeries && anyLoading ? (
          <div
            className="w-full flex flex-col items-center justify-center gap-2.5 text-xs text-muted-foreground"
            style={{ height: `${CHART_HEIGHT}px` }}
          >
            <div className="w-6 h-6 rounded-full border-2 border-primary border-t-transparent animate-spin" />
            <span>正在載入歷史行情與除息記錄並計算含息報酬...</span>
          </div>
        ) : !hasVisibleSeries ? (
          <div
            className="w-full flex flex-col items-center justify-center text-center gap-2 px-6"
            style={{ height: `${CHART_HEIGHT}px` }}
          >
            <div className="text-sm font-semibold text-foreground">
              無法取得所選標的之有效行情資料
            </div>
            <p className="text-xs text-muted-foreground">
              請檢查網路連線或改選其他標的。
            </p>
          </div>
        ) : (
          <div className="relative w-full">
            <svg
              role="img"
              aria-label="多標的同起點含息累積報酬率比較折線圖"
              viewBox={`0 0 ${chartWidth} ${CHART_HEIGHT}`}
              className="w-full overflow-visible select-none"
              style={{ height: `${CHART_HEIGHT}px` }}
            >
              {/* Horizontal Grid Lines & Y-Axis Labels */}
              {yAxis.ticks.map((tick) => {
                const y = scaleY(tick);
                const isZero = Math.abs(tick) < 0.001;
                return (
                  <g key={`y-${tick}`}>
                    <line
                      x1={PADDING_LEFT}
                      y1={y}
                      x2={chartWidth - PADDING_RIGHT}
                      y2={y}
                      stroke="currentColor"
                      className={
                        isZero
                          ? 'text-foreground/50'
                          : 'text-border/70'
                      }
                      strokeWidth={isZero ? 1.5 : 1}
                      strokeDasharray={isZero ? '5 4' : '2 3'}
                    />
                    <text
                      x={PADDING_LEFT - 10}
                      y={y + 4}
                      textAnchor="end"
                      fontSize="11.5"
                      fontWeight={isZero ? '700' : '500'}
                      className={`font-mono ${
                        isZero ? 'fill-foreground' : 'fill-muted-foreground'
                      }`}
                    >
                      {tick > 0 ? `+${tick.toFixed(1)}%` : `${tick.toFixed(1)}%`}
                    </text>
                  </g>
                );
              })}

              {/* Explicit 0.00% Zero Baseline (in case 0 was not on a tick boundary) */}
              {zeroY >= PADDING_TOP && zeroY <= CHART_HEIGHT - PADDING_BOTTOM && (
                <line
                  x1={PADDING_LEFT}
                  y1={zeroY}
                  x2={chartWidth - PADDING_RIGHT}
                  y2={zeroY}
                  stroke="currentColor"
                  className="text-foreground/60"
                  strokeWidth="1.5"
                  strokeDasharray="6 4"
                />
              )}

              {/* X-Axis Date Labels */}
              <g fontSize="11.5" className="fill-muted-foreground font-mono">
                {xTickIndices.map((idx, pos) => {
                  const x = scaleX(idx);
                  const anchor =
                    pos === 0
                      ? 'start'
                      : pos === xTickIndices.length - 1
                      ? 'end'
                      : 'middle';
                  return (
                    <text
                      key={`x-${idx}`}
                      x={x}
                      y={CHART_HEIGHT - 12}
                      textAnchor={anchor}
                    >
                      {comparisonModel.dates[idx]}
                    </text>
                  );
                })}
              </g>

              {/* Multi-Asset Normalized Total Return Lines */}
              {comparisonModel.series.map((s) => {
                const pathD = s.points.reduce(
                  (acc, val, idx) =>
                    `${acc}${idx === 0 ? 'M' : ' L'} ${scaleX(idx).toFixed(2)} ${scaleY(val).toFixed(2)}`,
                  ''
                );
                const lastIdx = s.points.length - 1;
                return (
                  <g key={s.ticker} data-testid={`compare-series-${s.ticker}`}>
                    <path
                      d={pathD}
                      fill="none"
                      stroke={s.color}
                      strokeWidth="2.2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                    {/* End dot */}
                    {lastIdx >= 0 && (
                      <circle
                        cx={scaleX(lastIdx)}
                        cy={scaleY(s.points[lastIdx])}
                        r="3.2"
                        fill={s.color}
                      />
                    )}
                  </g>
                );
              })}

              {/* Hover Vertical Crosshair & Active Points */}
              {hoveredTooltip && hoverIndex !== null && (
                <g pointerEvents="none">
                  <line
                    x1={hoveredTooltip.x}
                    y1={PADDING_TOP}
                    x2={hoveredTooltip.x}
                    y2={CHART_HEIGHT - PADDING_BOTTOM}
                    stroke="currentColor"
                    className="text-foreground/45"
                    strokeWidth="1.2"
                    strokeDasharray="3 3"
                  />
                  {comparisonModel.series.map((s) => {
                    const v = s.points[hoverIndex];
                    if (v === undefined) return null;
                    return (
                      <circle
                        key={`dot-${s.ticker}`}
                        cx={hoveredTooltip.x}
                        cy={scaleY(v)}
                        r="4.5"
                        fill={s.color}
                        stroke="var(--background, #ffffff)"
                        strokeWidth="1.8"
                      />
                    );
                  })}
                </g>
              )}

              {/* Transparent Interaction Capture Layer */}
              <rect
                data-testid="performance-compare-overlay"
                x={PADDING_LEFT}
                y={PADDING_TOP}
                width={plotWidth}
                height={plotHeight}
                fill="transparent"
                className="cursor-crosshair"
                onMouseMove={handleSvgMouseMove}
                onMouseLeave={() => setHoverIndex(null)}
              />
            </svg>

            {/* Floating DESC-Sorted Crosshair Tooltip */}
            {hoveredTooltip && (
              <div
                data-testid="performance-compare-tooltip"
                className="pointer-events-none absolute top-4 z-30 min-w-[235px] max-w-[300px] rounded-lg border border-border bg-popover/95 backdrop-blur-xs p-3 shadow-lg text-xs"
                style={{
                  left:
                    hoveredTooltip.x > chartWidth * 0.58
                      ? `${Math.max(12, hoveredTooltip.x - 270)}px`
                      : `${Math.min(chartWidth - 260, hoveredTooltip.x + 18)}px`,
                }}
              >
                <div className="font-mono font-bold text-foreground border-b border-border/60 pb-1.5 mb-2 flex items-center justify-between">
                  <span>{hoveredTooltip.date}</span>
                  <span className="text-[10px] font-normal text-muted-foreground">
                    含息累積報酬 (高→低)
                  </span>
                </div>
                <div className="space-y-1.5">
                  {hoveredTooltip.rows.map((row) => (
                    <div
                      key={row.ticker}
                      className="flex items-center justify-between gap-3"
                    >
                      <div className="flex items-center gap-1.5 min-w-0">
                        <span
                          className="w-2.5 h-2.5 rounded-full shrink-0"
                          style={{ backgroundColor: row.color }}
                        />
                        <span className="font-mono font-bold text-foreground shrink-0">
                          {row.ticker}
                        </span>
                        <span className="truncate text-muted-foreground">
                          {row.name}
                        </span>
                      </div>
                      <span
                        className={`font-mono font-bold shrink-0 ${getReturnColorClass(row.value)}`}
                      >
                        {formatReturnPct(row.value)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
