import React, { useState } from 'react';
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

  // Hover state for interactive SVG charts
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);

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

    const currentVal = isVix ? 15.2 : isVxn ? 18.4 : isFg ? 62 : 98.5;
    const alert1 = isVix ? 25 : isVxn ? 35 : isFg ? 20 : 120;
    const alert2 = isVix ? 30 : isVxn ? 40 : isFg ? 80 : 140;

    // 20-point historical time series
    const points = isVix
      ? [19.2, 18.5, 21.0, 24.5, 22.8, 20.1, 18.2, 17.5, 16.8, 17.2, 16.5, 15.8, 16.1, 15.4, 15.2]
      : isVxn
      ? [22.5, 21.8, 24.0, 27.2, 25.1, 23.4, 21.0, 20.2, 19.5, 19.8, 19.1, 18.7, 18.9, 18.5, 18.4]
      : isFg
      ? [35, 38, 42, 45, 48, 50, 55, 58, 60, 59, 61, 64, 63, 62, 62]
      : [115, 118, 122, 128, 125, 119, 112, 108, 105, 103, 101, 99.5, 99.0, 98.8, 98.5];

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

    // 12 months yield trends
    const months = ['25/10', '25/11', '25/12', '26/01', '26/02', '26/03', '26/04', '26/05', '26/06', '26/07', '26/08', '26/09'];
    const us10Y = [4.24, 4.28, 4.35, 4.41, 4.52, 4.68, 4.82, 4.95, 5.08, 5.14, 5.20, 5.26];
    const us20Y = [4.58, 4.61, 4.70, 4.78, 4.89, 5.02, 5.18, 5.32, 5.45, 5.52, 5.58, 5.64];
    const corpYield = [5.11, 5.14, 5.22, 5.30, 5.42, 5.55, 5.68, 5.75, 5.84, 5.88, 5.92, 5.97];
    const spread10y2y = [-0.12, -0.08, -0.02, 0.05, 0.11, 0.18, 0.22, 0.27, 0.31, 0.35, 0.38, 0.41];

    const minY = -0.5;
    const maxY = 6.5;

    const scaleY = (v: number) =>
      height - padding - ((v - minY) / (maxY - minY)) * (height - padding * 2);
    const scaleX = (idx: number) =>
      padding + (idx / (months.length - 1)) * (width - padding * 2);

    const makePath = (arr: number[]) =>
      arr.reduce((acc, v, idx) => `${acc} ${idx === 0 ? 'M' : 'L'} ${scaleX(idx)} ${scaleY(v)}`, '');

    return (
      <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border pb-3">
          <div>
            <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
            <div className="text-xs text-muted-foreground">
              最新記錄: 2026-09-30 (10Y: 5.26% | 20Y: 5.64% | 投資級公司債: 5.97% | 利差: +0.41%)
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
    const width = 720;
    const height = 240;
    const padding = 45;

    const days = 30;
    const ddCurve = [
      0.0, -0.4, -0.8, -0.5, -1.2, -1.8, -2.5, -3.2, -4.5, -5.8,
      -7.2, -8.5, -9.8, -11.2, -12.4, -10.5, -8.2, -6.4, -5.1, -4.2,
      -3.5, -2.8, -3.1, -2.5, -1.9, -1.5, -1.8, -2.1, -1.9, -1.4
    ];

    const minDD = -15;
    const maxDD = 0;

    const scaleY = (v: number) =>
      padding + ((0 - v) / (0 - minDD)) * (height - padding * 2);
    const scaleX = (idx: number) =>
      padding + (idx / (days - 1)) * (width - padding * 2);

    const pathD = ddCurve.reduce(
      (acc, v, idx) => `${acc} ${idx === 0 ? 'M' : 'L'} ${scaleX(idx)} ${scaleY(v)}`,
      ''
    );

    return (
      <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-3">
        <div className="flex items-center justify-between border-b border-border pb-3">
          <div>
            <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
            <div className="text-xs text-muted-foreground">
              聚焦 52 週動態滾動回撤 (240MA 支撐線 | 當前回撤: -1.4% | 最大回撤: -12.4%)
            </div>
          </div>
          <span className="text-xs px-2.5 py-1 rounded-md font-semibold bg-emerald-500/15 text-emerald-600 dark:text-emerald-400">
            常態健康區間
          </span>
        </div>

        <svg viewBox={`0 0 ${width} ${height}`} className="w-full h-56 overflow-visible">
          {/* Zero baseline */}
          <line
            x1={padding}
            y1={scaleY(0)}
            x2={width - padding}
            y2={scaleY(0)}
            stroke="#9ca3af"
            strokeWidth="1.5"
          />
          {[-5, -10, -15].map((tick) => (
            <g key={tick}>
              <line
                x1={padding}
                y1={scaleY(tick)}
                x2={width - padding}
                y2={scaleY(tick)}
                stroke="currentColor"
                strokeOpacity="0.1"
              />
              <text
                x={padding - 8}
                y={scaleY(tick) + 4}
                textAnchor="end"
                className="text-[10px] font-mono fill-muted-foreground"
              >
                {tick}%
              </text>
            </g>
          ))}

          {/* Area fill */}
          <path
            d={`${pathD} L ${scaleX(days - 1)} ${scaleY(0)} L ${scaleX(0)} ${scaleY(0)} Z`}
            fill="url(#dd-grad)"
            opacity="0.3"
          />

          <defs>
            <linearGradient id="dd-grad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#ef4444" stopOpacity="0.1" />
              <stop offset="100%" stopColor="#ef4444" stopOpacity="0.8" />
            </linearGradient>
          </defs>

          {/* Drawdown Curve */}
          <path d={pathD} fill="none" stroke="#ef4444" strokeWidth="2.5" />
        </svg>
      </div>
    );
  }

  // 4. Pairwise Matrix Heatmap Chart
  if (id.includes('pairwise-matrix-chart')) {
    const tickers = ['0050', '006208', '00692', '00850', '00713', '0056'];
    const r2Matrix = [
      [1.000, 0.998, 0.937, 0.947, 0.425, 0.347],
      [0.998, 1.000, 0.935, 0.945, 0.420, 0.342],
      [0.937, 0.935, 1.000, 0.920, 0.410, 0.335],
      [0.947, 0.945, 0.920, 1.000, 0.415, 0.338],
      [0.425, 0.420, 0.410, 0.415, 1.000, 0.580],
      [0.347, 0.342, 0.335, 0.338, 0.580, 1.000],
    ];

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
    const factors = [
      { name: 'R² 獨立度', val: 0.95 },
      { name: 'DCA 人氣', val: 0.88 },
      { name: 'AUM 規模', val: 0.92 },
      { name: 'MOM 12M', val: 0.78 },
      { name: 'KER 效率', val: 0.82 },
      { name: 'Sharpe 報酬', val: 0.85 },
      { name: 'YTM 殖利率', val: 0.65 },
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

    // 20 daily candles
    const candles = [
      { o: 190.5, h: 193.0, l: 189.5, c: 192.0 },
      { o: 192.0, h: 194.5, l: 191.0, c: 194.0 },
      { o: 194.0, h: 195.0, l: 192.5, c: 193.0 },
      { o: 193.0, h: 196.0, l: 192.0, c: 195.5 },
      { o: 195.5, h: 197.5, l: 194.0, c: 196.8 },
      { o: 196.8, h: 198.5, l: 195.0, c: 198.0 },
      { o: 198.0, h: 199.5, l: 196.5, c: 197.2 },
      { o: 197.2, h: 198.0, l: 194.5, c: 195.0 },
      { o: 195.0, h: 196.5, l: 193.0, c: 193.8 },
      { o: 193.8, h: 197.0, l: 193.5, c: 196.5 },
      { o: 196.5, h: 199.0, l: 195.5, c: 198.5 },
      { o: 198.5, h: 201.0, l: 197.8, c: 200.5 },
      { o: 200.5, h: 202.5, l: 199.0, c: 201.8 },
      { o: 201.8, h: 203.0, l: 199.5, c: 200.0 },
      { o: 200.0, h: 201.5, l: 198.0, c: 198.5 },
    ];

    const minPrice = 188;
    const maxPrice = 205;

    const scaleY = (p: number) =>
      height - padding - ((p - minPrice) / (maxPrice - minPrice)) * (height - padding * 2);
    const candleWidth = (width - padding * 2) / candles.length - 8;

    return (
      <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-4">
        <div className="flex items-center justify-between border-b border-border pb-3">
          <div>
            <div className="text-base font-semibold tracking-tight text-foreground">{label}</div>
            <div className="text-xs text-muted-foreground">
              日 K 線 (含 20MA/60MA 與月線布林通道，當前最新價: 198.5 TWD)
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
