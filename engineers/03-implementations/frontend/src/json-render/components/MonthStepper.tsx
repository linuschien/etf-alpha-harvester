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
  const props = directProps ?? element?.props ?? {};
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

  const now = new Date();
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;
  const defaultCurrentDate = `${currentYear}-${String(currentMonth).padStart(2, '0')}-01`;

  const parseYearMonth = (val?: string) => {
    if (!val) return { year: currentYear, month: currentMonth };
    const parts = val.split('-');
    const y = parseInt(parts[0], 10) || currentYear;
    const m = parseInt(parts[1], 10) || currentMonth;
    return { year: y, month: m };
  };

  const initialVal =
    (store?.get?.(bindPath) as string) ||
    (typeof props?.value === 'string' && props.value ? props.value : null) ||
    props.defaultValue ||
    defaultCurrentDate;
  const [{ year, month }, setYm] = useState(() => parseYearMonth(initialVal));

  useEffect(() => {
    if (store && bindPath && !store.get(bindPath) && props.defaultValue) {
      store.set(bindPath, props.defaultValue);
    }
  }, [store, bindPath, props.defaultValue]);

  useEffect(() => {
    if (typeof props?.value === 'string' && props.value) {
      setYm(parseYearMonth(props.value));
    }
  }, [props?.value]);

  useEffect(() => {
    if (!bindPath || !store?.subscribe) return;
    const unsub = store.subscribe(() => {
      const v = store.get(bindPath);
      if (typeof v === 'string' && v) {
        setYm(parseYearMonth(v));
      }
    });
    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [store, bindPath]);

  const activeVal =
    (store && bindPath ? (store.get(bindPath) as string) : null) ||
    (typeof props?.value === 'string' && props.value ? props.value : null);
  const activeYm = activeVal ? parseYearMonth(activeVal) : { year, month };
  const displayYear = activeYm.year;
  const displayMonth = activeYm.month;

  const updateMonth = (newY: number, newM: number) => {
    if (newY < 2020) return;
    if (newY > currentYear || (newY === currentYear && newM > currentMonth)) return; // Max out at latest month
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
    let newM = displayMonth - 1;
    let newY = displayYear;
    if (newM < 1) {
      newM = 12;
      newY -= 1;
    }
    updateMonth(newY, newM);
  };

  const nextMonth = () => {
    let newM = displayMonth + 1;
    let newY = displayYear;
    if (newM > 12) {
      newM = 1;
      newY += 1;
    }
    updateMonth(newY, newM);
  };

  const isLatest = displayYear === currentYear && displayMonth === currentMonth;

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
          {displayYear} 年 {displayMonth} 月{isLatest ? ' (最新)' : ''}
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

