import React, { useState, useMemo } from 'react';
import { useStateStore } from '@json-render/react';
import { mockDividendAnnouncements, mockCorporateActions } from '@/mocks/fixtures';

export interface EventCalendarProps {
  element?: {
    props?: Record<string, any>;
  };
  props?: Record<string, any>;
  className?: string;
}

export interface CalendarEvent {
  type: 'DIVIDEND_EX' | 'DIVIDEND_PAY' | 'SPLIT';
  date: string; // YYYY-MM-DD
  ticker: string;
  name?: string;
  amount?: number;
  taxTag?: string;
  freq?: string;
  splitInfo?: string;
}

export default function EventCalendar({ element, props: directProps }: EventCalendarProps) {
  let store: any = null;
  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  // Current calendar view state: default to 2026年 10月 (current system month)
  const now = new Date();
  const defaultYear = 2026;
  const defaultMonth = 10; // 1-indexed (October)

  const [year, setYear] = useState<number>(defaultYear);
  const [month, setMonth] = useState<number>(defaultMonth);
  const [viewMode, setViewMode] = useState<'calendar' | 'table'>('calendar');
  const [selectedFreq, setSelectedFreq] = useState<string>('全部');
  const [selectedDate, setSelectedDate] = useState<string | null>('2026-10-18');

  // Pull data from store or rich fallback fixtures
  const rawDividends: any[] = store?.get?.('/data/listDividendAnnouncements') || mockDividendAnnouncements;
  const rawSplits: any[] = store?.get?.('/data/listCorporateActions') || mockCorporateActions;

  // Build unified calendar event list
  const allEvents = useMemo<CalendarEvent[]>(() => {
    const list: CalendarEvent[] = [];

    // Dividends - Ex Date & Payment Date
    rawDividends.forEach((d) => {
      if (d.exDate) {
        const exDateStr = d.exDate.split('T')[0];
        list.push({
          type: 'DIVIDEND_EX',
          date: exDateStr,
          ticker: d.ticker,
          name: d.name || d.ticker,
          amount: d.dividendPerShare,
          taxTag: d.taxTag,
          freq: d.distributionFrequency || '季配',
        });
      }
      if (d.paymentDate) {
        const payDateStr = d.paymentDate.split('T')[0];
        list.push({
          type: 'DIVIDEND_PAY',
          date: payDateStr,
          ticker: d.ticker,
          name: d.name || d.ticker,
          amount: d.dividendPerShare,
          taxTag: d.taxTag,
          freq: d.distributionFrequency || '季配',
        });
      }
    });

    // Corporate actions - Splits
    rawSplits.forEach((s) => {
      if (s.effectiveDate) {
        const effDateStr = s.effectiveDate.split('T')[0];
        list.push({
          type: 'SPLIT',
          date: effDateStr,
          ticker: s.ticker,
          name: s.name || s.ticker,
          splitInfo: `${s.splitToShares || s.splitRatioNumerator || 2}:${s.splitFromShares || s.splitRatioDenominator || 1}`,
        });
      }
    });

    return list;
  }, [rawDividends, rawSplits]);

  // Filter events by frequency
  const filteredEvents = useMemo(() => {
    if (selectedFreq === '全部') return allEvents;
    return allEvents.filter((e) => e.freq === selectedFreq || e.type === 'SPLIT');
  }, [allEvents, selectedFreq]);

  // Group events by YYYY-MM-DD
  const eventsByDate = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>();
    filteredEvents.forEach((e) => {
      if (!map.has(e.date)) map.set(e.date, []);
      map.get(e.date)!.push(e);
    });
    return map;
  }, [filteredEvents]);

  // Month navigation
  const prevMonth = () => {
    if (month === 1) {
      setYear((y) => y - 1);
      setMonth(12);
    } else {
      setMonth((m) => m - 1);
    }
  };

  const nextMonth = () => {
    if (month === 12) {
      setYear((y) => y + 1);
      setMonth(1);
    } else {
      setMonth((m) => m + 1);
    }
  };

  const jumpToCurrentMonth = () => {
    setYear(defaultYear);
    setMonth(defaultMonth);
  };

  // Days in month calculation
  const daysInMonth = new Date(year, month, 0).getDate();
  const firstDayOfWeek = new Date(year, month - 1, 1).getDay(); // 0 = Sun

  const dayCells = useMemo(() => {
    const cells: { day: number | null; dateStr: string | null }[] = [];
    for (let i = 0; i < firstDayOfWeek; i++) {
      cells.push({ day: null, dateStr: null });
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      cells.push({ day: d, dateStr });
    }
    return cells;
  }, [year, month, daysInMonth, firstDayOfWeek]);

  // Events of selected date
  const selectedDayEvents = selectedDate ? eventsByDate.get(selectedDate) || [] : [];

  return (
    <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-6">
      {/* ── Top Bar: Title & View Mode & Month Navigation ── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <span>📅 ETF 除息月曆與股票分割事件視圖</span>
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            視覺化即時追蹤當月除息日、股利發放日與分割生效事件
          </p>
        </div>

        {/* View Switcher & Month Navigation */}
        <div className="flex flex-wrap items-center gap-2">
          {/* Freq Filter */}
          <select
            value={selectedFreq}
            onChange={(e) => setSelectedFreq(e.target.value)}
            className="px-3 py-1.5 text-xs font-medium rounded-lg border border-border bg-muted/30 text-foreground cursor-pointer"
          >
            <option value="全部">全部配息週期</option>
            <option value="月配">月配型</option>
            <option value="季配">季配型</option>
            <option value="半年配">半年配型</option>
            <option value="年配">年配型</option>
          </select>

          {/* Month Steppers */}
          <div className="flex items-center rounded-lg border border-border bg-muted/20 p-0.5">
            <button
              type="button"
              onClick={prevMonth}
              className="px-2 py-1 text-xs font-semibold rounded hover:bg-muted text-foreground transition"
              title="上個月"
            >
              ‹
            </button>
            <span className="px-3 py-1 text-xs font-bold font-mono text-foreground">
              {year} 年 {month} 月
            </span>
            <button
              type="button"
              onClick={nextMonth}
              className="px-2 py-1 text-xs font-semibold rounded hover:bg-muted text-foreground transition"
              title="下個月"
            >
              ›
            </button>
          </div>

          <button
            type="button"
            onClick={jumpToCurrentMonth}
            className="px-2.5 py-1.5 text-xs font-medium rounded-lg border border-border bg-secondary hover:bg-muted text-secondary-foreground transition"
          >
            當月 (10月)
          </button>

          {/* View Toggle */}
          <div className="flex rounded-lg border border-border bg-muted/40 p-0.5">
            <button
              type="button"
              onClick={() => setViewMode('calendar')}
              className={`px-3 py-1 text-xs font-semibold rounded-md transition ${
                viewMode === 'calendar'
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              月曆
            </button>
            <button
              type="button"
              onClick={() => setViewMode('table')}
              className={`px-3 py-1 text-xs font-semibold rounded-md transition ${
                viewMode === 'table'
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}
            >
              清單
            </button>
          </div>
        </div>
      </div>

      {/* ── Legend ── */}
      <div className="flex flex-wrap items-center gap-4 text-xs font-medium text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-emerald-500"></span>
          🟢 除息日 (買進參與配息)
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-blue-500"></span>
          🔵 發放日 (股利匯入帳戶)
        </span>
        <span className="flex items-center gap-1.5">
          <span className="w-2.5 h-2.5 rounded-full bg-purple-500"></span>
          🟣 股票分割生效日
        </span>
      </div>

      {/* ── Calendar Mode ── */}
      {viewMode === 'calendar' ? (
        <div className="space-y-4">
          <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
            {/* Weekday Headers */}
            <div className="grid grid-cols-7 border-b border-border bg-muted/40 text-center text-xs font-semibold text-muted-foreground py-2.5">
              <div className="text-destructive">週日</div>
              <div>週一</div>
              <div>週二</div>
              <div>週三</div>
              <div>週四</div>
              <div>週五</div>
              <div className="text-destructive">週六</div>
            </div>

            {/* Calendar Cells */}
            <div className="grid grid-cols-7 divide-x divide-y divide-border bg-card">
              {dayCells.map((cell, idx) => {
                if (!cell.day || !cell.dateStr) {
                  return (
                    <div
                      key={`empty-${idx}`}
                      className="min-h-[105px] bg-muted/10 p-2 border-t border-border"
                    />
                  );
                }

                const dayEvents = eventsByDate.get(cell.dateStr) || [];
                const isSelected = selectedDate === cell.dateStr;
                const isToday =
                  cell.day === now.getDate() &&
                  month === now.getMonth() + 1 &&
                  year === now.getFullYear();

                return (
                  <div
                    key={cell.dateStr}
                    onClick={() => setSelectedDate(cell.dateStr)}
                    className={`min-h-[105px] p-2 flex flex-col justify-between transition cursor-pointer hover:bg-muted/30 border-t border-border ${
                      isSelected ? 'ring-2 ring-primary ring-inset bg-primary/5' : ''
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span
                        className={`text-xs font-mono font-bold w-6 h-6 flex items-center justify-center rounded-full ${
                          isToday
                            ? 'bg-primary text-primary-foreground'
                            : isSelected
                            ? 'bg-muted font-black'
                            : 'text-foreground'
                        }`}
                      >
                        {cell.day}
                      </span>
                      {dayEvents.length > 0 && (
                        <span className="text-[10px] font-mono px-1.5 py-0.2 rounded-full bg-muted text-muted-foreground font-semibold">
                          {dayEvents.length}
                        </span>
                      )}
                    </div>

                    {/* Event Badges */}
                    <div className="space-y-1 my-1">
                      {dayEvents.slice(0, 3).map((e, eIdx) => {
                        if (e.type === 'DIVIDEND_EX') {
                          return (
                            <div
                              key={eIdx}
                              className="text-[10px] truncate px-1.5 py-0.5 rounded font-medium bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border border-emerald-500/30"
                              title={`${e.ticker} ${e.name} 除息 $${e.amount}`}
                            >
                              除息 {e.ticker} ${e.amount}
                            </div>
                          );
                        }
                        if (e.type === 'DIVIDEND_PAY') {
                          return (
                            <div
                              key={eIdx}
                              className="text-[10px] truncate px-1.5 py-0.5 rounded font-medium bg-blue-500/15 text-blue-700 dark:text-blue-300 border border-blue-500/30"
                              title={`${e.ticker} 股利發放`}
                            >
                              發放 {e.ticker}
                            </div>
                          );
                        }
                        return (
                          <div
                            key={eIdx}
                            className="text-[10px] truncate px-1.5 py-0.5 rounded font-medium bg-purple-500/15 text-purple-700 dark:text-purple-300 border border-purple-500/30"
                            title={`${e.ticker} 分割 ${e.splitInfo}`}
                          >
                            分割 {e.ticker} {e.splitInfo}
                          </div>
                        );
                      })}
                      {dayEvents.length > 3 && (
                        <div className="text-[9px] text-muted-foreground text-center font-medium">
                          +{dayEvents.length - 3} 更多事件
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* ── Selected Date Details Card ── */}
          {selectedDate && (
            <div className="rounded-xl border border-border bg-muted/20 p-4 space-y-3">
              <div className="flex items-center justify-between border-b border-border pb-2">
                <div className="text-sm font-bold text-foreground flex items-center gap-2">
                  <span>📌 {selectedDate} 事件明細</span>
                  <span className="text-xs px-2 py-0.5 rounded-full bg-primary/10 text-primary font-mono">
                    {selectedDayEvents.length} 項排程事件
                  </span>
                </div>
              </div>

              {selectedDayEvents.length === 0 ? (
                <div className="text-xs text-muted-foreground py-2 text-center">
                  本日無除息或分割事件排程。
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                  {selectedDayEvents.map((e, idx) => (
                    <div
                      key={idx}
                      className="rounded-lg border border-border bg-card p-3 shadow-xs space-y-1.5"
                    >
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-bold font-mono text-foreground">
                          {e.ticker} {e.name}
                        </span>
                        <span
                          className={`text-xs px-2 py-0.5 rounded-md font-semibold ${
                            e.type === 'DIVIDEND_EX'
                              ? 'bg-emerald-500/15 text-emerald-600'
                              : e.type === 'DIVIDEND_PAY'
                              ? 'bg-blue-500/15 text-blue-600'
                              : 'bg-purple-500/15 text-purple-600'
                          }`}
                        >
                          {e.type === 'DIVIDEND_EX'
                            ? '🟢 除息日'
                            : e.type === 'DIVIDEND_PAY'
                            ? '🔵 發放日'
                            : '🟣 股票分割'}
                        </span>
                      </div>

                      {e.amount !== undefined && (
                        <div className="text-xs text-muted-foreground">
                          每股金額: <span className="font-mono font-bold text-foreground">${e.amount} TWD</span>
                        </div>
                      )}
                      {e.taxTag && (
                        <div className="text-xs text-muted-foreground">
                          稅務標籤: <span className="font-medium text-foreground">{e.taxTag}</span>
                        </div>
                      )}
                      {e.splitInfo && (
                        <div className="text-xs text-muted-foreground">
                          分割比例: <span className="font-mono font-bold text-foreground">{e.splitInfo}</span>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      ) : (
        /* ── Table Mode ── */
        <div className="space-y-4">
          <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
            <table className="w-full text-left text-sm border-collapse">
              <thead className="bg-muted/40 text-muted-foreground text-xs font-semibold border-b border-border">
                <tr>
                  <th className="py-3 px-4">事件日期</th>
                  <th className="py-3 px-4">標的代碼</th>
                  <th className="py-3 px-4">標的名稱</th>
                  <th className="py-3 px-4">事件類型</th>
                  <th className="py-3 px-4">每股配息 / 分割比例</th>
                  <th className="py-3 px-4">稅務標籤</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filteredEvents.map((e, idx) => (
                  <tr key={idx} className="hover:bg-muted/20 transition">
                    <td className="py-3 px-4 font-mono font-medium">{e.date}</td>
                    <td className="py-3 px-4 font-mono font-bold text-primary">{e.ticker}</td>
                    <td className="py-3 px-4">{e.name}</td>
                    <td className="py-3 px-4">
                      <span
                        className={`text-xs px-2 py-0.5 rounded font-semibold ${
                          e.type === 'DIVIDEND_EX'
                            ? 'bg-emerald-500/15 text-emerald-600'
                            : e.type === 'DIVIDEND_PAY'
                            ? 'bg-blue-500/15 text-blue-600'
                            : 'bg-purple-500/15 text-purple-600'
                        }`}
                      >
                        {e.type === 'DIVIDEND_EX'
                          ? '除息日'
                          : e.type === 'DIVIDEND_PAY'
                          ? '發放日'
                          : '股票分割'}
                      </span>
                    </td>
                    <td className="py-3 px-4 font-mono font-semibold">
                      {e.amount !== undefined ? `$${e.amount} TWD` : e.splitInfo || '-'}
                    </td>
                    <td className="py-3 px-4 text-xs text-muted-foreground">{e.taxTag || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
