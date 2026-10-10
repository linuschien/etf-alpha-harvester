import React, { useState, useEffect, useMemo } from 'react';
import { useStateStore, useStateValue } from '@json-render/react';
import { api } from '@/lib/api-client';

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
  amount?: number;
  taxTag?: string;
  splitInfo?: string;
}

export interface TableRowItem {
  id: string;
  ticker: string;
  sortDate: string; // for DESC ordering
  exDate: string; // YYYY-MM-DD
  paymentDate: string; // YYYY-MM-DD or '-'
  amountOrRatio: string;
  type: 'DIVIDEND' | 'SPLIT';
  taxTagOrNote: string;
}

export default function EventCalendar({ element, props: directProps }: EventCalendarProps) {
  let store: any = null;
  let activeTab: string | undefined = undefined;
  let activeDcaSubTab: string | undefined = undefined;

  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  try {
    activeTab = useStateValue<string>('/activeTab');
    activeDcaSubTab = useStateValue<string>('/activeDcaSubTab');
  } catch {
    activeTab = undefined;
    activeDcaSubTab = undefined;
  }

  // Only consider active/visible if we are on the calendar tab and calendar subtab
  const isVisible =
    (!activeTab || activeTab === 'all' || activeTab === 'dca-calendar-section') &&
    (!activeDcaSubTab || activeDcaSubTab === 'all' || activeDcaSubTab === 'dca-subtab-calendar');

  // System reference time & defaults
  const defaultYear = 2026;
  const defaultMonth = 10; // 1-indexed (October)

  // Calendar mode state
  const [year, setYear] = useState<number>(defaultYear);
  const [month, setMonth] = useState<number>(defaultMonth);
  const [viewMode, setViewMode] = useState<'calendar' | 'table'>('calendar');
  const [selectedDate, setSelectedDate] = useState<string | null>(null);

  // Month dividends data (Lazy loaded by month for calendar)
  const [calendarDividends, setCalendarDividends] = useState<any[]>([]);
  const [isCalendarLoading, setIsCalendarLoading] = useState<boolean>(false);

  // Table mode text search state
  const [tickerInput, setTickerInput] = useState<string>('');
  const [searchedTicker, setSearchedTicker] = useState<string | null>(null);
  const [tableDividends, setTableDividends] = useState<any[]>([]);
  const [isTableLoading, setIsTableLoading] = useState<boolean>(false);

  // Corporate actions (Splits) - in-memory from store, shared between calendar & table
  const rawSplits: any[] = store?.get?.('/data/listCorporateActions') || [];

  // Store fallback for calendar dividends (e.g. seeded in unit test store)
  const storeDividends: any[] = store?.get?.('/data/listDividendAnnouncements') || [];

  // Calculate days in month for calendar
  const daysInMonth = new Date(year, month, 0).getDate();
  const firstDayOfWeek = new Date(year, month - 1, 1).getDay(); // 0 = Sun
  const monthStartDate = `${year}-${String(month).padStart(2, '0')}-01`;
  const monthEndDate = `${year}-${String(month).padStart(2, '0')}-${String(daysInMonth).padStart(2, '0')}`;

  // ── Fetch Calendar Month Dividends (Lazy loading by month) ───────────────
  useEffect(() => {
    if (!isVisible || viewMode !== 'calendar') return;

    let isMounted = true;
    setIsCalendarLoading(true);

    api
      .graphql<{ listDividendAnnouncements: any[] }>(
        `query ListDividendAnnouncements($filter: DividendAnnouncementFilterInput) {
          listDividendAnnouncements(filter: $filter) {
            ticker
            exDate
            dividendPerShare
            paymentDate
            taxTag
          }
        }`,
        { filter: { startDate: monthStartDate, endDate: monthEndDate } }
      )
      .then((data) => {
        if (!isMounted) return;
        const list = data?.listDividendAnnouncements || [];
        setCalendarDividends(list);
        setIsCalendarLoading(false);
      })
      .catch((err) => {
        if (!isMounted) return;
        // Fallback to store if API fails or when running in mock-less store environment
        setCalendarDividends(storeDividends);
        setIsCalendarLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [isVisible, year, month, monthStartDate, monthEndDate, viewMode]);

  // ── Fetch Table Dividends on Demand (Exact Ticker search) ─────────────────
  useEffect(() => {
    if (!isVisible || viewMode !== 'table' || !searchedTicker) {
      setTableDividends([]);
      setIsTableLoading(false);
      return;
    }

    let isMounted = true;
    setIsTableLoading(true);

    api
      .graphql<{ listDividendAnnouncements: any[] }>(
        `query ListDividendAnnouncements($filter: DividendAnnouncementFilterInput) {
          listDividendAnnouncements(filter: $filter) {
            ticker
            exDate
            dividendPerShare
            paymentDate
            taxTag
          }
        }`,
        { filter: { ticker: searchedTicker } }
      )
      .then((data) => {
        if (!isMounted) return;
        const list = data?.listDividendAnnouncements || [];
        setTableDividends(list);
        setIsTableLoading(false);
      })
      .catch((err) => {
        if (!isMounted) return;
        // Fallback: filter from storeDividends if present
        const filtered = storeDividends.filter(
          (d) => d.ticker?.toUpperCase() === searchedTicker.toUpperCase()
        );
        setTableDividends(filtered);
        setIsTableLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [isVisible, searchedTicker, viewMode]);

  // ── Build Unified Calendar Event List (Calendar View) ────────────────────
  const effectiveCalendarDividends =
    calendarDividends.length > 0 ? calendarDividends : storeDividends;

  const calendarEventsByDate = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>();

    const addEvent = (dateStr: string, event: CalendarEvent) => {
      if (!map.has(dateStr)) map.set(dateStr, []);
      map.get(dateStr)!.push(event);
    };

    // 1. Corporate Actions - Splits (Prioritized)
    rawSplits.forEach((s) => {
      if (s.effectiveDate) {
        const effDateStr = s.effectiveDate.split('T')[0];
        const splitRatio = `${s.splitToShares || s.splitRatioNumerator || 2}:${s.splitFromShares || s.splitRatioDenominator || 1}`;
        addEvent(effDateStr, {
          type: 'SPLIT',
          date: effDateStr,
          ticker: s.ticker,
          splitInfo: splitRatio,
        });
      }
    });

    // 2. Dividends - Ex-Date & Payment-Date
    effectiveCalendarDividends.forEach((d) => {
      if (d.exDate) {
        const exDateStr = d.exDate.split('T')[0];
        addEvent(exDateStr, {
          type: 'DIVIDEND_EX',
          date: exDateStr,
          ticker: d.ticker,
          amount: d.dividendPerShare,
          taxTag: d.taxTag,
        });
      }
      if (d.paymentDate) {
        const payDateStr = d.paymentDate.split('T')[0];
        addEvent(payDateStr, {
          type: 'DIVIDEND_PAY',
          date: payDateStr,
          ticker: d.ticker,
          amount: d.dividendPerShare,
          taxTag: d.taxTag,
        });
      }
    });

    // Sort events on each date: SPLIT (0) > DIVIDEND_EX (1) > DIVIDEND_PAY (2)
    const priority = (type: string) => {
      if (type === 'SPLIT') return 0;
      if (type === 'DIVIDEND_EX') return 1;
      return 2;
    };

    map.forEach((events) => {
      events.sort((a, b) => priority(a.type) - priority(b.type));
    });

    return map;
  }, [effectiveCalendarDividends, rawSplits]);

  // Calendar Day Cells
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

  // Selected date events in calendar mode
  const selectedDayEvents = selectedDate ? calendarEventsByDate.get(selectedDate) || [] : [];

  // ── Build Paired Rows for Table Mode (Exact Ticker Lookup) ───────────────
  const tableRows = useMemo<TableRowItem[]>(() => {
    if (!searchedTicker) return [];

    const rows: TableRowItem[] = [];

    // 1. Dividends paired into 1 single row per announcement
    tableDividends.forEach((d, idx) => {
      const exStr = d.exDate ? d.exDate.split('T')[0] : '-';
      const payStr = d.paymentDate ? d.paymentDate.split('T')[0] : '-';
      const sortDate = exStr !== '-' ? exStr : payStr;

      let taxLabel = '-';
      if (d.taxTag === 'DOMESTIC_54C') taxLabel = '54C 境內股利';
      else if (d.taxTag === 'OVERSEAS_76W') taxLabel = '76W 海外所得';
      else if (d.taxTag) taxLabel = String(d.taxTag);

      rows.push({
        id: `div-${d.ticker}-${exStr}-${idx}`,
        ticker: d.ticker,
        sortDate,
        exDate: exStr,
        paymentDate: payStr,
        amountOrRatio: d.dividendPerShare !== undefined ? `$${d.dividendPerShare} TWD` : '-',
        type: 'DIVIDEND',
        taxTagOrNote: taxLabel,
      });
    });

    // 2. Shared Corporate Actions (Splits) for this ticker from in-memory pool
    const matchingSplits = rawSplits.filter(
      (s) => s.ticker?.toUpperCase() === searchedTicker.toUpperCase()
    );

    matchingSplits.forEach((s, idx) => {
      const effStr = s.effectiveDate ? s.effectiveDate.split('T')[0] : '-';
      const splitRatio = `${s.splitToShares || s.splitRatioNumerator || 2}:${s.splitFromShares || s.splitRatioDenominator || 1}`;

      rows.push({
        id: `split-${s.ticker}-${effStr}-${idx}`,
        ticker: s.ticker,
        sortDate: effStr,
        exDate: effStr,
        paymentDate: '-',
        amountOrRatio: splitRatio,
        type: 'SPLIT',
        taxTagOrNote: '股票分割生效',
      });
    });

    // Sort descending (DESC, newest date first)
    rows.sort((a, b) => b.sortDate.localeCompare(a.sortDate));

    return rows;
  }, [searchedTicker, tableDividends, rawSplits]);

  // Month navigation handlers
  const prevMonth = () => {
    if (month === 1) {
      setYear((y) => y - 1);
      setMonth(12);
    } else {
      setMonth((m) => m - 1);
    }
    setSelectedDate(null);
  };

  const nextMonth = () => {
    if (month === 12) {
      setYear((y) => y + 1);
      setMonth(1);
    } else {
      setMonth((m) => m + 1);
    }
    setSelectedDate(null);
  };

  const jumpToCurrentMonth = () => {
    setYear(defaultYear);
    setMonth(defaultMonth);
    setSelectedDate(null);
  };

  // Search submission in Table mode
  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const clean = tickerInput.trim().toUpperCase();
    setSearchedTicker(clean || null);
  };

  const handleClearSearch = () => {
    setTickerInput('');
    setSearchedTicker(null);
    setTableDividends([]);
  };

  return (
    <div className="w-full rounded-xl border border-border bg-card p-6 shadow-sm space-y-6">
      {/* ── Top Bar: Title & Controls ── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-border pb-4">
        <div>
          <h2 className="text-xl font-bold tracking-tight text-foreground flex items-center gap-2">
            <span>📅 ETF 除息月曆與股票分割事件視圖</span>
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            視覺化追蹤當月除息日、股利發放日與分割生效事件
          </p>
        </div>

        {/* Dynamic Controls based on viewMode */}
        <div className="flex flex-wrap items-center gap-3">
          {viewMode === 'calendar' ? (
            /* Calendar Mode Controls: Month Steppers */
            <div className="flex items-center gap-2">
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
            </div>
          ) : (
            /* Table Mode Controls: Ticker Text Search Input */
            <form onSubmit={handleSearchSubmit} className="flex items-center gap-2">
              <div className="relative flex items-center">
                <input
                  type="text"
                  value={tickerInput}
                  onChange={(e) => setTickerInput(e.target.value)}
                  placeholder="搜尋標的代碼 (如 0050)..."
                  className="w-48 sm:w-56 px-3 py-1.5 text-xs font-mono rounded-lg border border-border bg-background text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-primary"
                />
                {tickerInput && (
                  <button
                    type="button"
                    onClick={handleClearSearch}
                    className="absolute right-2 text-muted-foreground hover:text-foreground text-xs"
                    title="清除"
                  >
                    ✕
                  </button>
                )}
              </div>
              <button
                type="submit"
                className="px-3 py-1.5 text-xs font-semibold rounded-lg bg-primary text-primary-foreground hover:bg-primary/90 transition shadow-xs cursor-pointer"
              >
                查詢
              </button>
            </form>
          )}

          {/* View Toggle (月曆 / 清單) */}
          <div className="flex rounded-lg border border-border bg-muted/40 p-0.5">
            <button
              type="button"
              onClick={() => setViewMode('calendar')}
              className={`px-3 py-1 text-xs font-semibold rounded-md transition cursor-pointer ${
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
              className={`px-3 py-1 text-xs font-semibold rounded-md transition cursor-pointer ${
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
          🟣 股票分割生效日 (優先置頂)
        </span>
      </div>

      {/* ── Mode 1: Calendar View ── */}
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

                const dayEvents = calendarEventsByDate.get(cell.dateStr) || [];
                const isSelected = selectedDate === cell.dateStr;
                const isToday =
                  cell.day === new Date().getDate() &&
                  month === new Date().getMonth() + 1 &&
                  year === new Date().getFullYear();

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

                    {/* Event Badges (Split prioritized at top) */}
                    <div className="space-y-1 my-1">
                      {dayEvents.slice(0, 3).map((e, eIdx) => {
                        if (e.type === 'SPLIT') {
                          return (
                            <div
                              key={eIdx}
                              className="text-[10px] truncate px-1.5 py-0.5 rounded font-semibold bg-purple-500/15 text-purple-700 dark:text-purple-300 border border-purple-500/30"
                              title={`${e.ticker} 分割 ${e.splitInfo}`}
                            >
                              🟣 分割 {e.ticker} {e.splitInfo}
                            </div>
                          );
                        }
                        if (e.type === 'DIVIDEND_EX') {
                          return (
                            <div
                              key={eIdx}
                              className="text-[10px] truncate px-1.5 py-0.5 rounded font-medium bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border border-emerald-500/30"
                              title={`${e.ticker} 除息 $${e.amount}`}
                            >
                              除息 {e.ticker} ${e.amount}
                            </div>
                          );
                        }
                        return (
                          <div
                            key={eIdx}
                            className="text-[10px] truncate px-1.5 py-0.5 rounded font-medium bg-blue-500/15 text-blue-700 dark:text-blue-300 border border-blue-500/30"
                            title={`${e.ticker} 股利發放`}
                          >
                            發放 {e.ticker}
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
                        {/* Clean Ticker Display - No redundant name */}
                        <span className="text-base font-black font-mono tracking-tight text-primary">
                          {e.ticker}
                        </span>
                        <span
                          className={`text-xs px-2 py-0.5 rounded-md font-semibold ${
                            e.type === 'SPLIT'
                              ? 'bg-purple-500/15 text-purple-600'
                              : e.type === 'DIVIDEND_EX'
                              ? 'bg-emerald-500/15 text-emerald-600'
                              : 'bg-blue-500/15 text-blue-600'
                          }`}
                        >
                          {e.type === 'SPLIT'
                            ? '🟣 股票分割'
                            : e.type === 'DIVIDEND_EX'
                            ? '🟢 除息日'
                            : '🔵 發放日'}
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
        /* ── Mode 2: Table View (Exact Ticker Lookup & Paired Ex/Pay Date Rows) ── */
        <div className="space-y-4">
          {!searchedTicker ? (
            /* State 1: Default Empty Prompt */
            <div className="rounded-xl border border-dashed border-border bg-muted/10 p-12 text-center space-y-2">
              <div className="text-2xl">🔍</div>
              <div className="text-sm font-semibold text-foreground">
                請輸入標的代碼以查詢歷史除息與分割記錄
              </div>
              <p className="text-xs text-muted-foreground max-w-md mx-auto">
                在上方輸入框輸入 ETF 代碼（例如 0050、0056 或 00878）並點擊「查詢」，即可檢視該標的之歷史除息日、發放日及股票分割記錄。
              </p>
            </div>
          ) : isTableLoading ? (
            /* State 2: Loading */
            <div className="rounded-xl border border-border bg-muted/10 p-8 text-center text-xs text-muted-foreground">
              正在查詢標的「{searchedTicker}」之歷史除息與分割記錄...
            </div>
          ) : tableRows.length === 0 ? (
            /* State 3: No Match */
            <div className="rounded-xl border border-destructive/20 bg-destructive/5 p-8 text-center space-y-1.5">
              <div className="text-sm font-bold text-destructive">
                ⚠️ 查無標的「{searchedTicker}」的除息或分割記錄
              </div>
              <p className="text-xs text-muted-foreground">
                查無相關除息公告或股票分割事件，請確認輸入之代碼是否正確。
              </p>
            </div>
          ) : (
            /* State 4: Paired Table Results */
            <div className="rounded-xl border border-border bg-card overflow-hidden shadow-sm">
              <table className="w-full text-left text-sm border-collapse">
                <thead className="bg-muted/40 text-muted-foreground text-xs font-semibold border-b border-border">
                  <tr>
                    <th className="py-3 px-4">標的代碼</th>
                    <th className="py-3 px-4">除息日 / 生效日</th>
                    <th className="py-3 px-4">發放日</th>
                    <th className="py-3 px-4">每股配息 / 分割比例</th>
                    <th className="py-3 px-4">稅務標籤 / 備註</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {tableRows.map((row) => (
                    <tr key={row.id} className="hover:bg-muted/20 transition">
                      <td className="py-3 px-4 font-mono font-bold text-primary">
                        {row.ticker}
                      </td>
                      <td className="py-3 px-4 font-mono font-medium">
                        <span className="flex items-center gap-1.5">
                          {row.type === 'SPLIT' ? (
                            <span className="text-purple-600 font-semibold">🟣 {row.exDate}</span>
                          ) : (
                            <span className="text-emerald-600 font-semibold">🟢 {row.exDate}</span>
                          )}
                        </span>
                      </td>
                      <td className="py-3 px-4 font-mono font-medium">
                        {row.paymentDate !== '-' ? (
                          <span className="text-blue-600 font-semibold">🔵 {row.paymentDate}</span>
                        ) : (
                          <span className="text-muted-foreground">-</span>
                        )}
                      </td>
                      <td className="py-3 px-4 font-mono font-semibold">
                        {row.amountOrRatio}
                      </td>
                      <td className="py-3 px-4 text-xs text-muted-foreground">
                        {row.taxTagOrNote}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
