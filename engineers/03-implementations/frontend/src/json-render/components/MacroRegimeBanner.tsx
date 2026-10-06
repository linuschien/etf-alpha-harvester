import React from 'react';
import { useStateStore } from '@json-render/react';

export interface MacroRegimeBannerProps {
  id?: string;
  data_ref?: string;
  className?: string;
}

export default function MacroRegimeBanner({
  element,
  props: directProps,
}: any) {
  const props: MacroRegimeBannerProps = element?.props ?? directProps ?? {};

  let store: any = null;
  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  const [, setTick] = React.useState(0);
  React.useEffect(() => {
    if (!store?.subscribe) return;
    const unsub = store.subscribe(() => {
      setTick((t) => t + 1);
    });
    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [store]);

  const regimeData =
    store?.get?.('/data/getMacroRegime') ||
    store?.get?.('/macroRegime') ||
    {};
  const yieldSnapshot =
    store?.get?.('/data/getLatestMacroYieldSnapshot') || {};

  const rawState = String(regimeData.macroState || 'HIGH_YIELD_ACCUMULATION').toUpperCase();

  let stateLabel = rawState;
  let stateIcon = '🟢';
  let stateBadgeClass =
    'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800';

  if (rawState.includes('HIGH_YIELD') || rawState.includes('ACCUMULATION')) {
    stateLabel = '高利蓄水期 (HIGH_YIELD_ACCUMULATION)';
    stateIcon = '🟢';
    stateBadgeClass =
      'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800';
  } else if (rawState.includes('NORMAL') || rawState.includes('BALANCED')) {
    stateLabel = '常態平衡期 (NORMAL_BALANCED)';
    stateIcon = '🔵';
    stateBadgeClass =
      'bg-blue-100 text-blue-800 dark:bg-blue-950/60 dark:text-blue-300 border-blue-300 dark:border-blue-800';
  } else if (rawState.includes('LOW_YIELD') || rawState.includes('HARVEST')) {
    stateLabel = '低利收割期 (LOW_YIELD_HARVEST)';
    stateIcon = '🟡';
    stateBadgeClass =
      'bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-300 border-amber-300 dark:border-amber-800';
  }

  // Calculate allocation percentages
  let equityPct = 80;
  if (regimeData.recommendedEquityRatio !== undefined && regimeData.recommendedEquityRatio !== null) {
    equityPct =
      regimeData.recommendedEquityRatio <= 1
        ? Math.round(regimeData.recommendedEquityRatio * 100)
        : Math.round(regimeData.recommendedEquityRatio);
  }
  let bondPct = 100 - equityPct;
  if (regimeData.recommendedBondRatio !== undefined && regimeData.recommendedBondRatio !== null) {
    bondPct =
      regimeData.recommendedBondRatio <= 1
        ? Math.round(regimeData.recommendedBondRatio * 100)
        : Math.round(regimeData.recommendedBondRatio);
  }

  // Crisis level
  const rawCrisis = String(regimeData.crisisLevel || 'NORMAL').toUpperCase();
  let crisisLabel = '🛡️ 危機等級: 正常 (NORMAL)';
  let crisisBadgeClass =
    'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 border-slate-300 dark:border-slate-700';

  if (rawCrisis.includes('CRISIS_LEVEL_2')) {
    crisisLabel = '🔥 黑天鵝救災 (CRISIS_LEVEL_2)';
    crisisBadgeClass =
      'bg-rose-100 text-rose-800 dark:bg-rose-950/60 dark:text-rose-300 border-rose-300 dark:border-rose-800';
  } else if (rawCrisis.includes('CRISIS_LEVEL_1')) {
    crisisLabel = '🚨 恐慌抄底 (CRISIS_LEVEL_1)';
    crisisBadgeClass =
      'bg-orange-100 text-orange-800 dark:bg-orange-950/60 dark:text-orange-300 border-orange-300 dark:border-orange-800';
  } else if (rawCrisis.includes('CORRECTION')) {
    crisisLabel = '⚠️ 修正期警示 (CORRECTION)';
    crisisBadgeClass =
      'bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-300 border-amber-300 dark:border-amber-800';
  }

  const effectiveDate =
    regimeData.effectiveDate ||
    (yieldSnapshot.recordDate ? String(yieldSnapshot.recordDate).slice(0, 10) : '') ||
    '2026-09-30';

  const assessmentSummary =
    regimeData.assessmentSummary ||
    '經濟基本面穩定擴張，長短天期利差正常化，高收益與投資級信用利差維持低位。';

  return (
    <div
      id={props.id || 'macro-regime-banner'}
      className={`rounded-xl border bg-card text-card-foreground shadow-sm p-5 space-y-4 ${
        props.className ?? ''
      }`}
    >
      {/* Top Header Row */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 border-b pb-3.5 border-border">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-lg bg-primary/10 text-primary font-bold text-lg select-none">
            🧭
          </div>
          <div>
            <h3 className="text-base font-semibold text-foreground tracking-tight">
              宏觀景氣循環與配置策略
            </h3>
            <p className="text-xs text-muted-foreground mt-0.5">
              依據企業債殖利率門檻與市場回撤幅度動態評估資產配置比例
            </p>
          </div>
        </div>

        <div className="flex items-center flex-wrap gap-2">
          {/* Macro State Badge */}
          <span
            className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold border ${stateBadgeClass}`}
          >
            <span>{stateIcon}</span>
            <span>{stateLabel}</span>
          </span>

          {/* Crisis Level Badge */}
          <span
            className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium border ${crisisBadgeClass}`}
          >
            {crisisLabel}
          </span>

          {/* Effective Date */}
          {effectiveDate && (
            <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-md text-xs text-muted-foreground bg-muted/60">
              <span>📅</span>
              <span>評估基準日: {effectiveDate}</span>
            </span>
          )}
        </div>
      </div>

      {/* Middle: Full Assessment Summary Text */}
      <div className="rounded-lg bg-muted/40 border border-muted-foreground/15 p-3.5 flex items-start gap-3">
        <span className="text-lg shrink-0 mt-0.5 select-none">📢</span>
        <div className="space-y-1">
          <div className="text-xs font-semibold text-muted-foreground">
            當前宏觀評估摘要與策略導引
          </div>
          <p className="text-sm font-medium text-foreground leading-relaxed">
            {assessmentSummary}
          </p>
        </div>
      </div>

      {/* Bottom: Asset Allocation Visual Bar */}
      <div className="bg-background/80 rounded-lg p-3 border border-border/80 space-y-2">
        <div className="flex items-center justify-between text-xs font-semibold">
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-blue-600 inline-block" />
            <span>
              建議股票配置:{' '}
              <span className="text-blue-600 dark:text-blue-400 font-bold">
                {equityPct}%
              </span>
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-600 inline-block" />
            <span>
              建議防禦債券配置:{' '}
              <span className="text-emerald-600 dark:text-emerald-400 font-bold">
                {bondPct}%
              </span>
            </span>
          </div>
        </div>
        <div className="h-2.5 w-full rounded-full bg-muted flex overflow-hidden">
          <div
            style={{ width: `${equityPct}%` }}
            className="bg-blue-600 h-full transition-all duration-300"
          />
          <div
            style={{ width: `${bondPct}%` }}
            className="bg-emerald-600 h-full transition-all duration-300"
          />
        </div>
      </div>
    </div>
  );
}

