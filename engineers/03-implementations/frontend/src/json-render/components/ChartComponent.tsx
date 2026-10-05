import React, { useEffect, useState } from 'react';
import { useStateStore } from '@json-render/react';

export interface ChartProps {
  element?: {
    props?: {
      id?: string;
      label?: string;
      data_ref?: string;
      className?: string;
    };
  };
  props?: {
    id?: string;
    label?: string;
    data_ref?: string;
    className?: string;
  };
}

export default function ChartComponent({ element, props: directProps }: ChartProps) {
  const props = element?.props ?? directProps ?? {};
  const id = props.id ?? '';
  const label = props.label ?? '圖表視覺化';

  let store: any = null;
  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  const [, setTick] = useState(0);
  useEffect(() => {
    if (!store?.subscribe) return;
    const unsub = store.subscribe(() => {
      setTick((t) => t + 1);
    });
    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [store]);

  // Hover state for interactive SVG charts
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);

  // Responsive chart width
  const containerRef = React.useRef<HTMLDivElement>(null);
  const [chartWidth, setChartWidth] = useState(860);
  useEffect(() => {
    if (!containerRef.current) return;
    const updateWidth = () => {
      if (containerRef.current) {
        const w = containerRef.current.clientWidth;
        if (w > 300) setChartWidth(w);
      }
    };
    updateWidth();
    if (typeof ResizeObserver !== 'undefined') {
      const ro = new ResizeObserver(updateWidth);
      ro.observe(containerRef.current);
      return () => ro.disconnect();
    }
  }, []);

  // 1. Sparkline / Emotion Gauge Charts
  if (id.includes('vix') || id.includes('vxn') || id.includes('move') || id.includes('fear-greed')) {
    const isVix = id.includes('vix') && !id.includes('vxn');
    const isVxn = id.includes('vxn');
    const isFg = id.includes('fear-greed');

    const title = isVix
      ? '^VIX 恐慌指數 (S&P 500 波動率)'
      : isVxn
      ? '^VXN 那斯達克波動指數'
      : isFg
      ? 'CNN Fear & Greed 雷達'
      : '^MOVE 美國公債波動指數';

    const alert1 = isVix ? 25 : isVxn ? 35 : isFg ? 20 : 120;
    const alert2 = isVix ? 30 : isVxn ? 40 : isFg ? 80 : 140;

    const ticker = isVix ? '^VIX' : isVxn ? '^VXN' : isFg ? 'FEAR_GREED' : '^MOVE';
    const tickerKey = isVix ? 'vix' : isVxn ? 'vxn' : isFg ? 'fearGreed' : 'move';
    const quoteSeries: any[] =
      store?.get?.(`/data/quoteTimeSeries/${ticker}`) ||
      store?.get?.('/data/quoteTimeSeries')?.[tickerKey] ||
      [];

    if (!quoteSeries || quoteSeries.length === 0) {
      return (
        <div className="w-full rounded-xl border border-border bg-card p-4 shadow-sm flex flex-col justify-between animate-pulse">
          <div className="flex items-center justify-between mb-2">
            <div>
              <div className="text-xs font-medium text-muted-foreground">{title}</div>
              <div className="text-xl font-bold font-mono tracking-tight text-foreground flex items-center gap-2">
                --
              </div>
            </div>
            <div className="text-right text-xs font-mono text-muted-foreground">
              <div>警戒: {alert1}</div>
              <div>恐慌: {alert2}</div>
            </div>
          </div>
          <div className="w-full h-28 bg-muted/20 rounded flex items-center justify-center text-xs text-muted-foreground">
            載入走勢中...
          </div>
        </div>
      );
    }

    const points = quoteSeries.map((q: any) => Number(q.closePrice));
    const currentVal = points[points.length - 1];

    const min = Math.min(...points, alert1) * 0.85;
    const max = Math.max(...points, alert2) * 1.15;
    const width = 360;
    const height = 140;
    const padding = 20;

    const coords = points.map((val, idx) => {
      const x = padding + (idx / (points.length - 1)) * (width - padding * 2);
      const y = height - padding - ((val - min) / (max - min)) * (height - padding * 2);
      return { x, y, val };
    });

    const pathD = coords.reduce((acc, c, idx) => `${acc} ${idx === 0 ? 'M' : 'L'} ${c.x} ${c.y}`, '');
    const alert1Y = height - padding - ((alert1 - min) / (max - min)) * (height - padding * 2);
    const alert2Y = height - padding - ((alert2 - min) / (max - min)) * (height - padding * 2);

    return (
      <div className="w-full rounded-xl border border-border bg-card p-4 shadow-sm flex flex-col justify-between">
        <div className="flex items-center justify-between mb-2">
          <div>
            <div className="text-xs font-medium text-muted-foreground">{title}</div>
            <div className="text-xl font-bold font-mono tracking-tight text-foreground flex items-center gap-2">
              {currentVal}
              <span
                className={`text-xs px-2 py-0.5 rounded-full font-semibold ${
                  currentVal > alert1
                    ? 'bg-destructive/15 text-destructive'
                    : 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400'
                }`}
              >
                {currentVal > alert1 ? '警戒區間' : '健康常態'}
              </span>
            </div>
          </div>
          <div className="text-right text-xs font-mono text-muted-foreground">
            <div>警戒: {alert1}</div>
            <div>恐慌: {alert2}</div>
          </div>
        </div>

        <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-28 overflow-visible">
          {/* Alert Threshold Lines */}
          <line
            x1={padding}
            y1={alert1Y}
            x2={width - padding}
            y2={alert1Y}
            stroke="#f59e0b"
            strokeDasharray="4 4"
            strokeWidth="1.2"
          />
          <line
            x1={padding}
            y1={alert2Y}
            x2={width - padding}
            y2={alert2Y}
            stroke="#ef4444"
            strokeDasharray="4 4"
            strokeWidth="1.2"
          />

          {/* Area fill */}
          <path
            d={`${pathD} L ${coords[coords.length - 1].x} ${height - padding} L ${coords[0].x} ${
              height - padding
            } Z`}
            fill={isFg ? 'url(#fg-grad)' : 'url(#spark-grad)'}
            opacity="0.25"
          />

          <defs>
            <linearGradient id="spark-grad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#3b82f6" />
              <stop offset="100%" stopColor="#3b82f6" stopOpacity="0" />
            </linearGradient>
            <linearGradient id="fg-grad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#10b981" />
              <stop offset="100%" stopColor="#10b981" stopOpacity="0" />
            </linearGradient>
          </defs>

          {/* Curve */}
          <path
            d={pathD}
            fill="none"
            stroke={isFg ? '#10b981' : '#3b82f6'}
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />

          {/* End Point Dot */}
          <circle
            cx={coords[coords.length - 1].x}
            cy={coords[coords.length - 1].y}
            r="4.5"
            fill={isFg ? '#10b981' : '#3b82f6'}
            className="animate-pulse"
          />
        </svg>
      </div>
    );
  }

  // 2. Macro Yield Curves Chart
  if (id.includes('macro-yield-chart')) {
    const width = 720;
    const height = 280;
    const padding = 45;

    const yieldHistory: any[] = store?.get?.('/data/listMacroYieldSnapshots') || [];
    const latestSnapshot: any = store?.get?.('/data/getLatestMacroYieldSnapshot') || null;

    if (!yieldHistory || yieldHistory.length === 0) {
      return (
        <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-4 animate-pulse">
          <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
          <div className="h-64 w-full bg-muted/20 rounded flex items-center justify-center text-xs text-muted-foreground">
            宏觀殖利率時間序列載入中...
          </div>
        </div>
      );
    }

    const months = yieldHistory.map((s: any) => String(s.recordDate || '').slice(2, 7));
    const us10Y = yieldHistory.map((s: any) => Number(s.us10YearTreasuryYield));
    const us20Y = yieldHistory.map((s: any) => Number(s.us20YearTreasuryYield));
    const corpYield = yieldHistory.map((s: any) => Number(s.usCorporateBondEffectiveYield));
    const spread10y2y = yieldHistory.map((s: any) => Number(s.yieldSpread10yMinus2y));

    const latest = latestSnapshot || yieldHistory[yieldHistory.length - 1];
    const latestDate = latest?.recordDate ? String(latest.recordDate).slice(0, 10) : '--';
    const subheaderText = `最新記錄: ${latestDate} (10Y: ${latest?.us10YearTreasuryYield ?? '--'}% | 20Y: ${latest?.us20YearTreasuryYield ?? '--'}% | 投資級公司債: ${latest?.usCorporateBondEffectiveYield ?? '--'}% | 利差: ${latest?.yieldSpread10yMinus2y !== undefined ? (latest.yieldSpread10yMinus2y >= 0 ? '+' : '') + latest.yieldSpread10yMinus2y + '%' : '--'})`;

    const minY = -0.5;
    const maxY = 6.5;

    const scaleY = (v: number) =>
      height - padding - ((v - minY) / (maxY - minY)) * (height - padding * 2);
    const scaleX = (idx: number) =>
      padding + (idx / Math.max(1, months.length - 1)) * (width - padding * 2);

    const makePath = (arr: number[]) =>
      arr.reduce((acc, v, idx) => `${acc} ${idx === 0 ? 'M' : 'L'} ${scaleX(idx)} ${scaleY(v)}`, '');

    return (
      <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3">
          <div>
            <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
            <div className="text-xs text-muted-foreground">
              {subheaderText}
            </div>
          </div>
          <div className="flex items-center gap-4 text-xs font-medium">
            <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-full bg-blue-500"></span> 10年美債</span>
            <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-full bg-purple-500"></span> 20年美債</span>
            <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-full bg-emerald-500"></span> 公司債</span>
            <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded-full bg-amber-500"></span> 10Y-2Y利差</span>
          </div>
        </div>

        <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-64 overflow-visible">
          {/* Grid lines */}
          {[0, 2, 3.5, 5, 6].map((tick) => (
            <g key={tick}>
              <line
                x1={padding}
                y1={scaleY(tick)}
                x2={width - padding}
                y2={scaleY(tick)}
                stroke="currentColor"
                strokeOpacity="0.1"
                strokeDasharray={tick === 3.5 || tick === 5.0 ? '4 4' : undefined}
              />
              <text
                x={padding - 8}
                y={scaleY(tick) + 4}
                textAnchor="end"
                className="text-[10px] font-mono fill-muted-foreground"
              >
                {tick.toFixed(1)}%
              </text>
            </g>
          ))}

          {/* Reference Lines */}
          <line
            x1={padding}
            y1={scaleY(5.0)}
            x2={width - padding}
            y2={scaleY(5.0)}
            stroke="#ef4444"
            strokeDasharray="4 4"
            strokeWidth="1.2"
          />
          <text x={width - padding + 5} y={scaleY(5.0) + 3} className="text-[10px] fill-red-500 font-semibold">
            5.0% 蓄水線
          </text>

          <line
            x1={padding}
            y1={scaleY(3.5)}
            x2={width - padding}
            y2={scaleY(3.5)}
            stroke="#10b981"
            strokeDasharray="4 4"
            strokeWidth="1.2"
          />
          <text x={width - padding + 5} y={scaleY(3.5) + 3} className="text-[10px] fill-emerald-500 font-semibold">
            3.5% 收割線
          </text>

          <line
            x1={padding}
            y1={scaleY(0.0)}
            x2={width - padding}
            y2={scaleY(0.0)}
            stroke="#6b7280"
            strokeDasharray="2 2"
            strokeWidth="1"
          />

          {/* Month labels on X axis */}
          {months.map((m, idx) => (
            <text
              key={m}
              x={scaleX(idx)}
              y={height - padding + 16}
              textAnchor="middle"
              className="text-[10px] font-mono fill-muted-foreground"
            >
              {m}
            </text>
          ))}

          {/* Lines */}
          <path d={makePath(corpYield)} fill="none" stroke="#10b981" strokeWidth="2.5" />
          <path d={makePath(us20Y)} fill="none" stroke="#8b5cf6" strokeWidth="2.5" />
          <path d={makePath(us10Y)} fill="none" stroke="#3b82f6" strokeWidth="2.5" />
          <path d={makePath(spread10y2y)} fill="none" stroke="#f59e0b" strokeWidth="2" strokeDasharray="3 3" />
        </svg>
      </div>
    );
  }

  // 3. Drawdown Radar / 52W Drawdown Chart
  if (id.includes('drawdown-radar-chart')) {
    const selectedBenchmark: string =
      store?.get?.('/filters/drawdown-benchmark-selector') || '^TWII';
    const selectedWindow: string =
      store?.get?.('/filters/drawdown-window-selector') || '6M';
    const showMA: boolean =
      store?.get?.('/filters/toggle-ma-switch') !== false;
    const showBB: boolean =
      store?.get?.('/filters/toggle-bb-switch') !== false;
    const showFib: boolean =
      store?.get?.('/filters/toggle-fib-switch') !== false;

    const benchmarkKeyMap: Record<string, string> = {
      '^TWII': 'twii',
      '^GSPC': 'gspc',
      '^NDX': 'ndx',
      '^SOX': 'sox',
      '^N225': 'n225',
    };
    const benchmarkNameMap: Record<string, string> = {
      '^TWII': '台股加權指數',
      '^GSPC': '標普 500 指數',
      '^NDX': '那斯達克 100 指數',
      '^SOX': '費城半導體指數',
      '^N225': '日經 225 指數',
    };

    const bKey = benchmarkKeyMap[selectedBenchmark] || 'twii';
    const bName = benchmarkNameMap[selectedBenchmark] || selectedBenchmark;

    const allQuotes: any[] =
      store?.get?.(`/data/quoteTimeSeries/${selectedBenchmark}`) ||
      store?.get?.('/data/quoteTimeSeries')?.[bKey] ||
      [];

    if (!allQuotes || allQuotes.length === 0) {
      return (
        <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-4 animate-pulse">
          <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
          <div className="h-80 w-full bg-muted/20 rounded flex items-center justify-center text-xs text-muted-foreground">
            {bName} 回撤雷達時間序列載入中...
          </div>
        </div>
      );
    }

    // Sort quotes ascending by tradeDate
    const sortedQuotes = [...allQuotes].sort((a, b) =>
      String(a.tradeDate || '').localeCompare(String(b.tradeDate || ''))
    );

    // Full series 52-week peak from cache
    const peak52W = Math.max(
      ...sortedQuotes.map((q) => Number(q.highPrice || q.closePrice || 0))
    );

    // Slice window
    let windowQuotes = sortedQuotes;
    if (selectedWindow === '1M') windowQuotes = sortedQuotes.slice(-22);
    else if (selectedWindow === '3M') windowQuotes = sortedQuotes.slice(-66);
    else if (selectedWindow === '6M') windowQuotes = sortedQuotes.slice(-132);
    else if (selectedWindow === '1Y') windowQuotes = sortedQuotes.slice(-264);
    if (windowQuotes.length === 0) windowQuotes = sortedQuotes;

    const pointsCount = Math.max(2, windowQuotes.length);
    const latestQuote = windowQuotes[windowQuotes.length - 1];
    const latestPrice = Number(latestQuote.closePrice);
    const currentDD = Number((((latestPrice - peak52W) / peak52W) * 100).toFixed(2));

    const ddPoints = windowQuotes.map((q) =>
      Number((((Number(q.closePrice) - peak52W) / peak52W) * 100).toFixed(2))
    );
    const maxDD = Math.min(...ddPoints);

    // Window extremes (anchor for Fibonacci Retracement within active Time Window)
    const windowPriceMax = Math.max(
      ...windowQuotes.map((q) => Number(q.highPrice || q.closePrice || 0))
    );
    const windowPriceMin = Math.min(
      ...windowQuotes.map((q) => Number(q.lowPrice || q.closePrice || 0))
    );
    const windowRange = Math.max(1, windowPriceMax - windowPriceMin);

    // Fibonacci Retracement: 7 Levels anchored to Time Window [windowPriceMin, windowPriceMax]
    const fibLevels = [
      { pct: 0.0, label: '0.0% 波段頂部', color: '#64748b', pts: windowPriceMax },
      { pct: -23.6, label: '-23.6% 多頭呼吸線', color: '#10b981', pts: windowPriceMax - 0.236 * windowRange },
      { pct: -38.2, label: '-38.2% 黃金撈底線', color: '#f59e0b', pts: windowPriceMax - 0.382 * windowRange },
      { pct: -50.0, label: '-50.0% 多空平衡線', color: '#3b82f6', pts: windowPriceMax - 0.500 * windowRange },
      { pct: -61.8, label: '-61.8% 錯殺超跌線', color: '#8b5cf6', pts: windowPriceMax - 0.618 * windowRange },
      { pct: -76.4, label: '-76.4% 黑天鵝救災線', color: '#ef4444', pts: windowPriceMax - 0.764 * windowRange },
      { pct: -100.0, label: '-100.0% 波段地板', color: '#64748b', pts: windowPriceMin },
    ];

    // Compute Y-Axis Domain [yMin, yMax] based on all visible/active indicators
    let domainMax = windowPriceMax;
    let domainMin = windowPriceMin;

    if (showMA) {
      windowQuotes.forEach((q) => {
        if (q.ma20) { domainMax = Math.max(domainMax, Number(q.ma20)); domainMin = Math.min(domainMin, Number(q.ma20)); }
        if (q.ma60) { domainMax = Math.max(domainMax, Number(q.ma60)); domainMin = Math.min(domainMin, Number(q.ma60)); }
        if (q.ma120) { domainMax = Math.max(domainMax, Number(q.ma120)); domainMin = Math.min(domainMin, Number(q.ma120)); }
        if (q.ma240) { domainMax = Math.max(domainMax, Number(q.ma240)); domainMin = Math.min(domainMin, Number(q.ma240)); }
      });
    }

    if (showBB) {
      windowQuotes.forEach((q) => {
        if (q.bbUpper) domainMax = Math.max(domainMax, Number(q.bbUpper));
        if (q.bbLower) domainMin = Math.min(domainMin, Number(q.bbLower));
      });
    }

    // 5% breathing room padding
    const span = Math.max(1, domainMax - domainMin);
    const yMax = domainMax + span * 0.05;
    const yMin = domainMin - span * 0.05;

    // Layout coordinates
    const width = chartWidth || 860;
    const height = 340;
    const paddingLeft = 85;
    const paddingRight = 195;
    const paddingTop = 32;
    const paddingBottom = 42;

    const scaleY = (v: number) =>
      paddingTop + ((yMax - v) / (yMax - yMin)) * (height - paddingTop - paddingBottom);
    const scaleX = (idx: number) =>
      paddingLeft + (idx / (pointsCount - 1)) * (width - paddingLeft - paddingRight);

    // Close price path & gradient area
    const pricePath = windowQuotes.reduce(
      (acc, q, idx) =>
        `${acc} ${idx === 0 ? 'M' : 'L'} ${scaleX(idx)} ${scaleY(Number(q.closePrice))}`,
      ''
    );
    const priceAreaPath = `${pricePath} L ${scaleX(pointsCount - 1)} ${scaleY(yMin)} L ${scaleX(0)} ${scaleY(yMin)} Z`;

    // 4 Moving Average paths (20MA, 60MA, 120MA, 240MA)
    const makeMaPath = (key: 'ma20' | 'ma60' | 'ma120' | 'ma240') => {
      const valid = windowQuotes
        .map((q, idx) => ({ idx, val: q[key] ? Number(q[key]) : null }))
        .filter((p) => p.val !== null);
      if (valid.length === 0) return '';
      return valid.reduce(
        (acc, p, i) => `${acc} ${i === 0 ? 'M' : 'L'} ${scaleX(p.idx)} ${scaleY(p.val!)}`,
        ''
      );
    };

    const ma20Path = makeMaPath('ma20');
    const ma60Path = makeMaPath('ma60');
    const ma120Path = makeMaPath('ma120');
    const ma240Path = makeMaPath('ma240');

    // Bollinger Band envelope
    const bbValid = windowQuotes
      .map((q, idx) => ({
        idx,
        upper: q.bbUpper ? Number(q.bbUpper) : null,
        lower: q.bbLower ? Number(q.bbLower) : null,
      }))
      .filter((p) => p.upper !== null && p.lower !== null);

    let bbAreaPath = '';
    if (bbValid.length > 0) {
      const upperPart = bbValid.reduce(
        (acc, p, i) => `${acc} ${i === 0 ? 'M' : 'L'} ${scaleX(p.idx)} ${scaleY(p.upper!)}`,
        ''
      );
      const lowerPart = bbValid
        .slice()
        .reverse()
        .reduce(
          (acc, p) => `${acc} L ${scaleX(p.idx)} ${scaleY(p.lower!)}`,
          ''
        );
      bbAreaPath = `${upperPart} ${lowerPart} Z`;
    }

    // Y Axis Ticks (5 round point ticks)
    const yTickCount = 5;
    const yTicks = Array.from({ length: yTickCount }, (_, i) =>
      yMin + (i / (yTickCount - 1)) * (yMax - yMin)
    );

    // X Axis Date Labels
    const tickIndices = [
      0,
      Math.floor(pointsCount * 0.25),
      Math.floor(pointsCount * 0.5),
      Math.floor(pointsCount * 0.75),
      pointsCount - 1,
    ];
    const dateLabels = tickIndices.map((idx) => {
      const q = windowQuotes[Math.min(idx, pointsCount - 1)];
      return q?.tradeDate ? String(q.tradeDate).slice(5, 10) : '';
    });

    const formatPoints = (pts: number) => Math.round(pts).toLocaleString() + ' 點';

    // Status badge
    let statusBadge = {
      text: `🛡️ 多頭呼吸區 (回撤 ${currentDD}%)`,
      color: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400',
    };
    if (currentDD <= -76.4) {
      statusBadge = {
        text: `🚨 黑天鵝救災區 (回撤 ${currentDD}%)`,
        color: 'bg-rose-500/15 text-rose-600 dark:text-rose-400',
      };
    } else if (currentDD <= -61.8) {
      statusBadge = {
        text: `💎 超跌黃金坑 (回撤 ${currentDD}%)`,
        color: 'bg-purple-500/15 text-purple-600 dark:text-purple-400',
      };
    } else if (currentDD <= -50.0) {
      statusBadge = {
        text: `⚡ 多空平衡修正 (回撤 ${currentDD}%)`,
        color: 'bg-blue-500/15 text-blue-600 dark:text-blue-400',
      };
    } else if (currentDD <= -38.2) {
      statusBadge = {
        text: `⚠️ 黃金防守線加碼 (回撤 ${currentDD}%)`,
        color: 'bg-amber-500/15 text-amber-600 dark:text-amber-400',
      };
    } else if (currentDD <= -23.6) {
      statusBadge = {
        text: `📉 拉回修正區 (回撤 ${currentDD}%)`,
        color: 'bg-teal-500/15 text-teal-600 dark:text-teal-400',
      };
    }

    const hoverQuote = hoverIndex !== null ? windowQuotes[hoverIndex] : null;
    const hoverPrice = hoverQuote ? Number(hoverQuote.closePrice) : null;
    const hoverDate = hoverQuote?.tradeDate ? String(hoverQuote.tradeDate).slice(0, 10) : '';
    const hoverDD =
      hoverPrice !== null
        ? Number((((hoverPrice - peak52W) / peak52W) * 100).toFixed(2))
        : null;

    return (
      <div ref={containerRef} className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-4">
        {/* Dynamic Header with Key Indicators - Prominently showing Index Points */}
        <div className="flex flex-wrap items-center justify-between border-b border-border pb-3 gap-2">
          <div>
            <div className="text-base font-semibold tracking-tight text-foreground flex items-center gap-2">
              <span>{bName} ({selectedBenchmark}) 52 週回撤雷達</span>
              <span className="text-xs px-2 py-0.5 rounded font-mono bg-secondary text-secondary-foreground font-normal">
                視窗: {selectedWindow}
              </span>
            </div>
            <div className="text-xs text-muted-foreground mt-1 flex flex-wrap gap-x-4 gap-y-1">
              <span>最新指數點數: <strong className="text-sm font-mono font-bold text-rose-600 dark:text-rose-400">{latestPrice.toLocaleString()} 點</strong></span>
              <span>52 週最高點數: <strong className="text-foreground font-mono font-semibold">{peak52W.toLocaleString()} 點</strong></span>
              <span>當前回撤幅度: <strong className="text-foreground font-mono font-semibold">{currentDD}%</strong></span>
              <span>52 週最大回撤: <strong className="text-foreground font-mono font-semibold">{maxDD}%</strong></span>
            </div>
          </div>
          <span className={`text-xs px-3 py-1 rounded-md font-semibold ${statusBadge.color}`}>
            {statusBadge.text}
          </span>
        </div>

        {/* Legend Toolbar with 4 MAs, BB, and Fib 7 Levels */}
        <div className="flex flex-wrap items-center justify-between text-xs text-muted-foreground gap-2">
          <div className="flex flex-wrap items-center gap-4">
            <span className="flex items-center gap-1.5 font-medium text-foreground">
              <span className="w-3 h-1 bg-rose-500 rounded-full inline-block"></span>
              收盤走勢
            </span>
            {showMA && (
              <>
                <span className="flex items-center gap-1.5 font-medium text-amber-600 dark:text-amber-400">
                  <span className="w-3 h-0.5 bg-amber-500 inline-block"></span>
                  20MA (月線)
                </span>
                <span className="flex items-center gap-1.5 font-medium text-purple-600 dark:text-purple-400">
                  <span className="w-3 h-0.5 bg-purple-500 inline-block"></span>
                  60MA (季線)
                </span>
                <span className="flex items-center gap-1.5 font-medium text-orange-600 dark:text-orange-400">
                  <span className="w-3 h-0.5 bg-orange-500 inline-block"></span>
                  120MA (半年線)
                </span>
                <span className="flex items-center gap-1.5 font-medium text-blue-600 dark:text-blue-400">
                  <span className="w-3 h-0.5 bg-blue-500 inline-block"></span>
                  240MA (年線)
                </span>
              </>
            )}
            {showBB && (
              <span className="flex items-center gap-1.5 font-medium text-sky-600 dark:text-sky-400">
                <span className="w-3 h-2 bg-sky-500/20 border border-sky-400 rounded-sm inline-block"></span>
                布林通道 (20MA ± 2σ)
              </span>
            )}
            {showFib && (
              <span className="flex items-center gap-1.5 font-medium text-muted-foreground">
                <span className="w-3 h-0.5 border-b border-dashed border-foreground inline-block"></span>
                黃金分割 7 級防線 (0% ~ -100%)
              </span>
            )}
          </div>
          <span className="font-mono text-[11px] text-muted-foreground">
            52W Peak: {formatPoints(peak52W)} | 視窗區間: {formatPoints(windowPriceMin)} ~ {formatPoints(windowPriceMax)}
          </span>
        </div>

        {/* SVG Chart - Responsive Full Width & 0-Drift Precision Coordinates */}
        <svg
          viewBox={`0 0 ${width} ${height}`}
          preserveAspectRatio="none"
          className="w-full h-80 overflow-visible cursor-crosshair"
          onMouseMove={(e) => {
            const svg = e.currentTarget;
            const pt = svg.createSVGPoint();
            pt.x = e.clientX;
            pt.y = e.clientY;
            const ctm = svg.getScreenCTM();
            if (!ctm) return;
            const svgP = pt.matrixTransform(ctm.inverse());
            const chartAreaWidth = width - paddingLeft - paddingRight;
            if (svgP.x >= paddingLeft && svgP.x <= width - paddingRight) {
              const ratio = (svgP.x - paddingLeft) / chartAreaWidth;
              const idx = Math.min(
                pointsCount - 1,
                Math.max(0, Math.round(ratio * (pointsCount - 1)))
              );
              setHoverIndex(idx);
            }
          }}
          onMouseLeave={() => setHoverIndex(null)}
        >
          <defs>
            <linearGradient id="price-radar-grad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#ef4444" stopOpacity="0.25" />
              <stop offset="100%" stopColor="#ef4444" stopOpacity="0.02" />
            </linearGradient>
          </defs>

          {/* Left Y Axis Title */}
          <text
            x={paddingLeft - 8}
            y={paddingTop - 12}
            textAnchor="end"
            className="text-[10px] font-bold font-mono fill-muted-foreground"
          >
            指數點數 (回撤%)
          </text>

          {/* Left Y Axis Ticks (Real Points) */}
          {yTicks.map((tickVal) => {
            const ddFromPeak = Number((((tickVal - peak52W) / peak52W) * 100).toFixed(1));
            const sign = ddFromPeak > 0 ? '+' : '';
            return (
              <g key={tickVal}>
                <line
                  x1={paddingLeft}
                  y1={scaleY(tickVal)}
                  x2={width - paddingRight}
                  y2={scaleY(tickVal)}
                  stroke="currentColor"
                  strokeOpacity="0.08"
                />
                <text
                  x={paddingLeft - 8}
                  y={scaleY(tickVal) + 4}
                  textAnchor="end"
                  className="text-[10px] font-mono fill-muted-foreground"
                >
                  {formatPoints(tickVal)} ({sign}{ddFromPeak}%)
                </text>
              </g>
            );
          })}

          {/* Fibonacci Retracement Lines (7 Levels within Time Window) */}
          {showFib &&
            fibLevels.map((lvl) => {
              const y = scaleY(lvl.pts);
              return (
                <g key={lvl.pct}>
                  <line
                    x1={paddingLeft}
                    y1={y}
                    x2={width - paddingRight}
                    y2={y}
                    stroke={lvl.color}
                    strokeDasharray="4 4"
                    strokeWidth="1.2"
                  />
                  <text
                    x={width - paddingRight + 8}
                    y={y + 3}
                    style={{ fill: lvl.color }}
                    className="text-[10px] font-mono font-semibold"
                  >
                    {lvl.label} ({formatPoints(lvl.pts)})
                  </text>
                </g>
              );
            })}

          {/* Bollinger Band Shaded Cloud */}
          {showBB && bbAreaPath && (
            <path
              d={bbAreaPath}
              fill="#0ea5e9"
              fillOpacity="0.12"
              stroke="#0ea5e9"
              strokeWidth="0.8"
              strokeDasharray="2 2"
            />
          )}

          {/* Close Price Area Gradient */}
          <path d={priceAreaPath} fill="url(#price-radar-grad)" />

          {/* 4 Moving Average Curves */}
          {showMA && (
            <>
              {ma20Path && <path d={ma20Path} fill="none" stroke="#eab308" strokeWidth="1.8" />}
              {ma60Path && <path d={ma60Path} fill="none" stroke="#a855f7" strokeWidth="1.8" />}
              {ma120Path && <path d={ma120Path} fill="none" stroke="#f97316" strokeWidth="1.8" />}
              {ma240Path && <path d={ma240Path} fill="none" stroke="#3b82f6" strokeWidth="1.8" />}
            </>
          )}

          {/* Primary Close Price Curve */}
          <path d={pricePath} fill="none" stroke="#ef4444" strokeWidth="2.5" />

          {/* Latest Point Marker */}
          <g>
            <circle
              cx={scaleX(pointsCount - 1)}
              cy={scaleY(latestPrice)}
              r="5"
              fill="#ef4444"
              stroke="#ffffff"
              strokeWidth="2"
            />
            <rect
              x={Math.max(paddingLeft, scaleX(pointsCount - 1) - 165)}
              y={Math.max(paddingTop, scaleY(latestPrice) - 26)}
              width="160"
              height="20"
              rx="4"
              className="fill-background/95 stroke-rose-500 stroke-[1.5]"
            />
            <text
              x={Math.max(paddingLeft, scaleX(pointsCount - 1) - 165) + 80}
              y={Math.max(paddingTop, scaleY(latestPrice) - 26) + 14}
              textAnchor="middle"
              className="text-[10px] font-mono font-bold fill-rose-600 dark:fill-rose-400"
            >
              📍 最新: {latestPrice.toLocaleString()} 點 ({currentDD}%)
            </text>
          </g>

          {/* Interactive Hover Crosshair with Date & Exact Price Tooltip */}
          {hoverIndex !== null && hoverQuote && hoverPrice !== null && (
            <g>
              {/* Vertical Crosshair Line */}
              <line
                x1={scaleX(hoverIndex)}
                y1={paddingTop}
                x2={scaleX(hoverIndex)}
                y2={height - paddingBottom}
                stroke="#64748b"
                strokeDasharray="3 3"
                strokeWidth="1.2"
              />
              {/* Horizontal Crosshair Line */}
              <line
                x1={paddingLeft}
                y1={scaleY(hoverPrice)}
                x2={width - paddingRight}
                y2={scaleY(hoverPrice)}
                stroke="#64748b"
                strokeDasharray="3 3"
                strokeWidth="1.2"
              />
              {/* Intersection Dot */}
              <circle
                cx={scaleX(hoverIndex)}
                cy={scaleY(hoverPrice)}
                r="5"
                fill="#3b82f6"
                stroke="#ffffff"
                strokeWidth="2"
              />
              {/* Floating Tooltip Box */}
              {(() => {
                const boxW = 185;
                const boxH = 46;
                const rawX = scaleX(hoverIndex) - boxW / 2;
                const clampX = Math.max(paddingLeft + 5, Math.min(rawX, width - paddingRight - boxW - 5));
                const boxY = Math.max(paddingTop + 5, scaleY(hoverPrice) - boxH - 12);
                return (
                  <g>
                    <rect
                      x={clampX}
                      y={boxY}
                      width={boxW}
                      height={boxH}
                      rx="6"
                      className="fill-popover/95 stroke-border stroke shadow-xl"
                    />
                    <text
                      x={clampX + 12}
                      y={boxY + 18}
                      className="text-[11px] font-mono font-bold fill-foreground"
                    >
                      📅 {hoverDate}
                    </text>
                    <text
                      x={clampX + 12}
                      y={boxY + 35}
                      className="text-[11px] font-mono font-semibold fill-rose-600 dark:fill-rose-400"
                    >
                      📈 {formatPoints(hoverPrice)} ({hoverDD !== null && hoverDD >= 0 ? '+' : ''}{hoverDD}%)
                    </text>
                  </g>
                );
              })()}
            </g>
          )}

          {/* X Axis Date Labels */}
          {dateLabels.map((lbl, idx) => {
            const x =
              paddingLeft +
              (idx / (dateLabels.length - 1)) *
                (width - paddingLeft - paddingRight);
            return (
              <text
                key={`${lbl}-${idx}`}
                x={x}
                y={height - paddingBottom + 16}
                textAnchor="middle"
                className="text-[10px] font-mono fill-muted-foreground"
              >
                {lbl}
              </text>
            );
          })}
        </svg>
      </div>
    );
  }

  // 4. Pairwise Matrix Heatmap Chart
  if (id.includes('pairwise-matrix-chart')) {
    const rawMatrix: any[] = store?.get?.('/data/listPairwiseMatrix') || [];

    if (!rawMatrix || rawMatrix.length === 0) {
      return (
        <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-4 animate-pulse">
          <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
          <div className="h-64 w-full bg-muted/20 rounded flex items-center justify-center text-xs text-muted-foreground">
            正交相關性矩陣載入中...
          </div>
        </div>
      );
    }

    const tickerSet = new Set<string>();
    rawMatrix.forEach((m: any) => {
      if (m.baseTicker) tickerSet.add(m.baseTicker);
      if (m.targetTicker) tickerSet.add(m.targetTicker);
    });
    const tickers = Array.from(tickerSet).slice(0, 6);
    const r2Lookup = new Map<string, number>();
    rawMatrix.forEach((m: any) => {
      r2Lookup.set(`${m.baseTicker}-${m.targetTicker}`, Number(m.rSquared));
      r2Lookup.set(`${m.targetTicker}-${m.baseTicker}`, Number(m.rSquared));
    });
    const r2Matrix = tickers.map((t1) =>
      tickers.map((t2) => (t1 === t2 ? 1.0 : (r2Lookup.get(`${t1}-${t2}`) ?? 0.5)))
    );

    return (
      <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-4">
        <div className="flex items-center justify-between border-b border-border pb-3">
          <div>
            <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
            <div className="text-xs text-muted-foreground">
              兩兩資產 R² 判定矩陣（R² ≥ 0.50 判定為共線冗餘，綠色為正交獨立）
            </div>
          </div>
          <div className="flex items-center gap-3 text-xs font-medium">
            <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded bg-emerald-500"></span> 正交獨立 (R²&lt;0.5)</span>
            <span className="flex items-center gap-1.5"><span className="w-3 h-3 rounded bg-purple-500"></span> 共線冗餘 (R²≥0.5)</span>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-center text-sm font-mono border-collapse">
            <thead>
              <tr>
                <th className="p-2 border border-border bg-muted/40 font-semibold text-xs">基準 \ 目標</th>
                {tickers.map((t) => (
                  <th key={t} className="p-2 border border-border bg-muted/40 font-bold text-xs">{t}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {tickers.map((rowTicker, rIdx) => (
                <tr key={rowTicker}>
                  <td className="p-2 border border-border bg-muted/20 font-bold text-xs">{rowTicker}</td>
                  {tickers.map((colTicker, cIdx) => {
                    const r2 = r2Matrix[rIdx][cIdx];
                    const isSelf = rIdx === cIdx;
                    const isHigh = r2 >= 0.50 && !isSelf;

                    const bgColor = isSelf
                      ? 'bg-muted/30 text-muted-foreground'
                      : isHigh
                      ? 'bg-purple-500/20 text-purple-700 dark:text-purple-300 font-bold'
                      : 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 font-medium';

                    return (
                      <td key={colTicker} className={`p-3 border border-border ${bgColor}`}>
                        {r2.toFixed(3)}
                        {isHigh && <div className="text-[9px] text-destructive">共線</div>}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    );
  }

  // 5. Spider / Radar Chart
  if (id.includes('spider-radar-chart')) {
    const candidates: any[] = store?.get?.('/data/getOrthogonalCandidates') || [];

    if (!candidates || candidates.length === 0) {
      return (
        <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm flex flex-col items-center space-y-3 animate-pulse">
          <div className="w-full flex items-center justify-between border-b border-border pb-3">
            <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
            <span className="text-xs font-mono text-muted-foreground">7 因子綜合評分</span>
          </div>
          <div className="w-64 h-64 bg-muted/20 rounded-full flex items-center justify-center text-xs text-muted-foreground">
            因子評分載入中...
          </div>
        </div>
      );
    }

    const first = candidates[0];
    const factors = [
      { name: 'R² 獨立度', val: Math.min(1, Math.max(0.1, 1 - (first.rSquared ?? 0.5))) },
      { name: 'DCA 人氣', val: Math.min(1, Math.max(0.1, (20 - (first.dcaRank ?? 10)) / 20)) },
      { name: 'AUM 規模', val: Math.min(1, Math.max(0.1, (first.fundSizeTwd ?? 50) / 100)) },
      { name: 'MOM 12M', val: Math.min(1, Math.max(0.1, ((first.momentum12m ?? 0) + 0.3) / 0.6)) },
      { name: 'KER 效率', val: Math.min(1, Math.max(0.1, first.kerEfficiency ?? 0.7)) },
      { name: 'Sharpe 報酬', val: Math.min(1, Math.max(0.1, (first.sharpeRatio ?? 1.0) / 2.0)) },
      { name: 'YTM 殖利率', val: Math.min(1, Math.max(0.1, (first.dividendYield ?? 5) / 10)) },
    ];

    const size = 260;
    const center = size / 2;
    const radius = 95;
    const numAxes = factors.length;

    const angleSlice = (Math.PI * 2) / numAxes;

    const coords = factors.map((f, i) => {
      const angle = angleSlice * i - Math.PI / 2;
      const x = center + radius * f.val * Math.cos(angle);
      const y = center + radius * f.val * Math.sin(angle);
      return { x, y, ...f };
    });

    const polygonPath = coords.reduce(
      (acc, c, idx) => `${acc} ${idx === 0 ? 'M' : 'L'} ${c.x} ${c.y}`,
      ''
    ) + ' Z';

    return (
      <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm flex flex-col items-center space-y-3">
        <div className="w-full flex items-center justify-between border-b border-border pb-3">
          <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
          <span className="text-xs font-mono text-muted-foreground">7 因子綜合評分</span>
        </div>

        <svg viewBox={`0 0 ${size} ${size}`} className="w-64 h-64 overflow-visible">
          {/* Background Concentric Polygons */}
          {[0.25, 0.5, 0.75, 1.0].map((level) => {
            const poly = factors
              .map((_, i) => {
                const angle = angleSlice * i - Math.PI / 2;
                return `${center + radius * level * Math.cos(angle)},${
                  center + radius * level * Math.sin(angle)
                }`;
              })
              .join(' ');
            return (
              <polygon
                key={level}
                points={poly}
                fill="none"
                stroke="currentColor"
                strokeOpacity="0.15"
                strokeWidth="1"
              />
            );
          })}

          {/* Axes & Labels */}
          {factors.map((f, i) => {
            const angle = angleSlice * i - Math.PI / 2;
            const x2 = center + radius * Math.cos(angle);
            const y2 = center + radius * Math.sin(angle);
            const labelX = center + (radius + 20) * Math.cos(angle);
            const labelY = center + (radius + 15) * Math.sin(angle);

            return (
              <g key={f.name}>
                <line x1={center} y1={center} x2={x2} y2={y2} stroke="currentColor" strokeOpacity="0.2" />
                <text
                  x={labelX}
                  y={labelY}
                  textAnchor="middle"
                  className="text-[10px] font-medium fill-muted-foreground"
                >
                  {f.name}
                </text>
              </g>
            );
          })}

          {/* Factor Area */}
          <path d={polygonPath} fill="#3b82f6" fillOpacity="0.35" stroke="#3b82f6" strokeWidth="2.5" />

          {/* Vertex points */}
          {coords.map((c) => (
            <circle key={c.name} cx={c.x} cy={c.y} r="3.5" fill="#3b82f6" stroke="#ffffff" strokeWidth="1.5" />
          ))}
        </svg>
      </div>
    );
  }

  // 6. K-Line / Candlestick Chart
  if (id.includes('kline-chart')) {
    const width = 720;
    const height = 280;
    const padding = 45;

    const klineQuotes: any[] =
      store?.get?.('/data/quoteTimeSeries/^TWII') ||
      store?.get?.('/data/quoteTimeSeries')?.twii ||
      [];

    if (!klineQuotes || klineQuotes.length === 0) {
      return (
        <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-4 animate-pulse">
          <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
          <div className="h-64 w-full bg-muted/20 rounded flex items-center justify-center text-xs text-muted-foreground">
            K 線時間序列載入中...
          </div>
        </div>
      );
    }

    const candles = klineQuotes.slice(-20).map((q: any) => ({
      o: Number(q.openPrice ?? q.closePrice),
      h: Number(q.highPrice ?? q.closePrice),
      l: Number(q.lowPrice ?? q.closePrice),
      c: Number(q.closePrice),
    }));

    const minPrice = Math.min(...candles.map((c: any) => c.l)) * 0.99;
    const maxPrice = Math.max(...candles.map((c: any) => c.h)) * 1.01;
    const latestCandle = candles[candles.length - 1];

    const scaleY = (p: number) =>
      height - padding - ((p - minPrice) / Math.max(1, maxPrice - minPrice)) * (height - padding * 2);
    const candleWidth = (width - padding * 2) / candles.length - 8;

    return (
      <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-4">
        <div className="flex items-center justify-between border-b border-border pb-3">
          <div>
            <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
            <div className="text-xs text-muted-foreground">
              日 K 線 (當前最新價: {latestCandle.c.toLocaleString()} 點)
            </div>
          </div>
          <div className="flex items-center gap-3 text-xs font-medium">
            <span className="flex items-center gap-1.5"><span className="w-3 h-3 bg-red-500 rounded"></span> 上漲</span>
            <span className="flex items-center gap-1.5"><span className="w-3 h-3 bg-emerald-500 rounded"></span> 下跌</span>
            <span className="flex items-center gap-1.5"><span className="w-3 h-1 bg-amber-500 rounded"></span> 20MA</span>
          </div>
        </div>

        <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-64 overflow-visible">
          {/* Price grid */}
          {[190, 195, 200, 205].map((p) => (
            <g key={p}>
              <line x1={padding} y1={scaleY(p)} x2={width - padding} y2={scaleY(p)} stroke="currentColor" strokeOpacity="0.1" />
              <text x={padding - 8} y={scaleY(p) + 4} textAnchor="end" className="text-[10px] font-mono fill-muted-foreground">
                ${p}
              </text>
            </g>
          ))}

          {/* Candlesticks */}
          {candles.map((c, idx) => {
            const isUp = c.c >= c.o;
            const x = padding + idx * ((width - padding * 2) / candles.length) + 4;
            const yTop = scaleY(Math.max(c.o, c.c));
            const yBottom = scaleY(Math.min(c.o, c.c));
            const bodyHeight = Math.max(yBottom - yTop, 2);
            const wickX = x + candleWidth / 2;

            const color = isUp ? '#ef4444' : '#10b981';

            return (
              <g key={idx}>
                {/* Upper and Lower Wick */}
                <line x1={wickX} y1={scaleY(c.h)} x2={wickX} y2={scaleY(c.l)} stroke={color} strokeWidth="1.5" />
                {/* Candle Body */}
                <rect x={x} y={yTop} width={candleWidth} height={bodyHeight} fill={color} rx="1" />
              </g>
            );
          })}
        </svg>
      </div>
    );
  }

  // Fallback Generic Clean SVG Chart
  return (
    <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm flex flex-col items-center justify-center text-center space-y-2">
      <div className="text-xl font-bold font-mono text-foreground">{label}</div>
      <div className="text-xs text-muted-foreground">數據流來源: {props.data_ref || '自動整合指標'}</div>
    </div>
  );
}
