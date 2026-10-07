import React, { useState, useEffect } from 'react';
import { useStateStore } from '@json-render/react';

export interface MonthStepperProps {
  element?: any;
  props?: any;
  bindings?: Record<string, string>;
  emit?: (event: string, ...args: any[]) => void;
}

export default function MonthStepper({
  element,
  props: directProps,
  bindings,
  emit,
}: MonthStepperProps) {
  const props = element?.props ?? directProps ?? {};
  let store: any = null;
  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  const bindPath =
    bindings?.value ||
    (typeof props?.value === 'object' && props?.value?.$bindState
      ? props?.value?.$bindState
      : null) ||
    (typeof element?.props?.value === 'object' && element?.props?.value?.$bindState
      ? element?.props?.value?.$bindState
      : null) ||
    '/filters/leaderboard-evaluation-date';

  const parseYearMonth = (val?: string) => {
    if (!val) return { year: 2026, month: 9 };
    const parts = val.split('-');
    const y = parseInt(parts[0], 10) || 2026;
    const m = parseInt(parts[1], 10) || 9;
    return { year: y, month: m };
  };

  const initialVal = (store?.get?.(bindPath) as string) || props.defaultValue || '2026-09-01';
  const [{ year, month }, setYm] = useState(() => parseYearMonth(initialVal));

  useEffect(() => {
    if (!bindPath || !store?.subscribe) return;
    const unsub = store.subscribe(() => {
      const v = store.get(bindPath);
      if (typeof v === 'string') {
        setYm(parseYearMonth(v));
      }
    });
    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [store, bindPath]);

  const updateMonth = (newY: number, newM: number) => {
    if (newY < 2025) return;
    if (newY > 2026 || (newY === 2026 && newM > 9)) return; // Max out at latest month (2026/09)
    setYm({ year: newY, month: newM });
    const formattedDate = `${newY}-${String(newM).padStart(2, '0')}-01`;
    if (store && bindPath) {
      store.set(bindPath, formattedDate);
    }
    if (emit) {
      emit('change', formattedDate);
    }
  };

  const prevMonth = () => {
    let newM = month - 1;
    let newY = year;
    if (newM < 1) {
      newM = 12;
      newY -= 1;
    }
    updateMonth(newY, newM);
  };

  const nextMonth = () => {
    let newM = month + 1;
    let newY = year;
    if (newM > 12) {
      newM = 1;
      newY += 1;
    }
    updateMonth(newY, newM);
  };

  const isLatest = year === 2026 && month === 9;

  return (
    <div className={`flex items-center gap-2 py-1 ${props.className ?? ''}`}>
      {props.label && (
        <span className="text-xs font-semibold text-muted-foreground shrink-0">
          {props.label}：
        </span>
      )}
      <div className="flex items-center rounded-lg border border-border bg-muted/20 p-0.5 shadow-xs">
        <button
          type="button"
          onClick={prevMonth}
          className="px-2 py-1 text-xs font-semibold rounded hover:bg-muted text-foreground transition cursor-pointer select-none"
          title="上個月"
          aria-label="上個月"
        >
          ‹
        </button>
        <span className="px-3 py-1 text-xs font-bold font-mono text-foreground select-none">
          {year} 年 {month} 月{isLatest ? ' (最新)' : ''}
        </span>
        <button
          type="button"
          onClick={nextMonth}
          disabled={isLatest}
          className={`px-2 py-1 text-xs font-semibold rounded transition select-none ${
            isLatest
              ? 'text-muted-foreground/40 cursor-not-allowed'
              : 'hover:bg-muted text-foreground cursor-pointer'
          }`}
          title="下個月"
          aria-label="下個月"
        >
          ›
        </button>
      </div>
    </div>
  );
}

