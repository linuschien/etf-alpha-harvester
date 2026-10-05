import React from 'react';
import { useStateStore } from '@json-render/react';

export interface MetricCardProps {
  id?: string;
  label?: string;
  value?: string | number | { $bindState?: string };
  change?: string;
  sublabel?: string;
  data_ref?: string;
  className?: string;
  onClick?: () => void;
}

export default function MetricCard({
  element,
  props: directProps,
  emit,
}: any) {
  const props: MetricCardProps = element?.props ?? directProps ?? {};

  let store: any = null;
  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  let displayValue = props.value;
  if (
    displayValue &&
    typeof displayValue === 'object' &&
    '$bindState' in displayValue
  ) {
    displayValue = store?.get?.(displayValue.$bindState);
  }

  // If no direct value but store has metric by id, try reading it
  if (displayValue === undefined && props.id && store) {
    const valFromStore = store.get(`/metrics/${props.id}`);
    if (valFromStore !== undefined) {
      displayValue = valFromStore;
    }
  }

  // Check sublabel from props or store (for tradeDate)
  let sublabel = props.sublabel;
  if (!sublabel && props.id && store) {
    const fromStore =
      store.get(`/metrics/${props.id}-date`) ||
      store.get(`/metrics/${props.id}_date`) ||
      store.get(`/metrics/${props.id}/sublabel`) ||
      store.get(`/metrics/${props.id}/date`);
    if (fromStore) {
      sublabel =
        typeof fromStore === 'string' && fromStore.startsWith('交易日')
          ? fromStore
          : `交易日: ${fromStore}`;
    }
  }

  // Fallback date for 5 core benchmark quote cards
  if (
    !sublabel &&
    props.id &&
    ['metric-twii', 'metric-gspc', 'metric-ndx', 'metric-sox', 'metric-n225'].includes(
      props.id
    )
  ) {
    sublabel = '交易日: 2026-10-02';
  }

  let priceText =
    displayValue !== undefined && displayValue !== null
      ? String(displayValue)
      : '--';
  let changeText = props.change;

  // If priceText is formatted as "22,850.50 (+0.85%)" and no direct props.change
  if (!changeText && typeof priceText === 'string') {
    const match = priceText.match(/^(.*?)\s*(\([+-]?\d+(?:\.\d+)?%\))$/);
    if (match) {
      priceText = match[1];
      changeText = match[2].replace(/[()]/g, '');
    }
  }

  const handleClick = () => {
    if (store && props.id) {
      const tickerMap: Record<string, string> = {
        'metric-twii': '^TWII',
        'metric-gspc': '^GSPC',
        'metric-ndx': '^NDX',
        'metric-sox': '^SOX',
        'metric-n225': '^N225',
      };
      if (tickerMap[props.id]) {
        store.set('/filters/drawdown-benchmark-selector', tickerMap[props.id]);
      }
    }
    if (emit) emit('click');
    if (props.onClick) props.onClick();
  };

  return (
    <div
      onClick={handleClick}
      className={`p-4 rounded-xl border bg-card text-card-foreground shadow-sm transition-all hover:shadow-md cursor-pointer ${
        props.className ?? ''
      }`}
    >
      <div className="text-xs font-medium text-muted-foreground line-clamp-1">
        {props.label}
      </div>
      <div className="text-2xl font-bold mt-1 tracking-tight">
        {priceText}
      </div>
      {(changeText || sublabel) && (
        <div className="flex items-center text-xs text-muted-foreground mt-1.5 gap-2">
          {changeText && (
            <span
              className={`font-semibold ${
                changeText.startsWith('+')
                  ? 'text-emerald-600 dark:text-emerald-400'
                  : changeText.startsWith('-')
                  ? 'text-rose-600 dark:text-rose-400'
                  : 'text-muted-foreground'
              }`}
            >
              {changeText}
            </span>
          )}
          {sublabel && (
            <span className="flex items-center gap-1 text-[11px] text-muted-foreground/80">
              <span>📅</span>
              <span>{sublabel}</span>
            </span>
          )}
        </div>
      )}
    </div>
  );
}
