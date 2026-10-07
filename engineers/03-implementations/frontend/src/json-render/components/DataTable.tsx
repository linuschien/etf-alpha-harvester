import React, { useState, useMemo } from 'react';
import { useStateStore } from '@json-render/react';

export interface DataTableColumn {
  field: string;
  label: string;
  sortable?: boolean;
  default_sort?: 'asc' | 'desc';
}

export interface DataTableProps {
  id?: string;
  label?: string;
  columns?: DataTableColumn[];
  data?: any[] | { $bindState?: string };
  onRowClick?: (row: any) => void;
  className?: string;
}

export default function DataTable({
  element,
  props: directProps,
  children,
  emit,
}: any) {
  const props: DataTableProps = element?.props ?? directProps ?? {};
  const columns: DataTableColumn[] = props.columns ?? [];

  // Resolve data either from props or from JSONUI state store
  let store: any = null;
  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  let dataList: any[] = [];
  const rawData = props.data;
  if (Array.isArray(rawData)) {
    dataList = rawData;
  } else if (rawData && typeof rawData === 'object' && '$bindState' in rawData) {
    const fromStore = store?.get?.(rawData.$bindState);
    if (Array.isArray(fromStore)) {
      dataList = fromStore;
    }
  }

  // Find default sort column
  const defaultSortCol = columns.find((c) => c.default_sort);
  const [sortField, setSortField] = useState<string | null>(
    defaultSortCol?.field ?? null
  );
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>(
    defaultSortCol?.default_sort ?? 'asc'
  );

  // Expanded state for hierarchical cluster rows
  const [expandedRows, setExpandedRows] = useState<Record<string, boolean>>({});

  const toggleExpand = (rowKey: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setExpandedRows((prev) => ({
      ...prev,
      [rowKey]: !prev[rowKey],
    }));
  };

  const handleSort = (field: string) => {
    if (sortField === field) {
      setSortDirection((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortDirection('asc');
    }
  };

  const sortedData = useMemo(() => {
    if (!sortField) return dataList;
    return [...dataList].sort((a, b) => {
      const valA = a?.leader ? a.leader[sortField] ?? a[sortField] : a[sortField];
      const valB = b?.leader ? b.leader[sortField] ?? b[sortField] : b[sortField];
      if (valA === valB) return 0;
      if (valA === undefined || valA === null) return 1;
      if (valB === undefined || valB === null) return -1;
      if (typeof valA === 'number' && typeof valB === 'number') {
        return sortDirection === 'asc' ? valA - valB : valB - valA;
      }
      const strA = String(valA);
      const strB = String(valB);
      return sortDirection === 'asc'
        ? strA.localeCompare(strB)
        : strB.localeCompare(strA);
    });
  }, [dataList, sortField, sortDirection]);

  const handleRowClick = (row: any) => {
    const ticker = row.ticker || row.leader?.ticker;
    if (store && ticker) {
      store.set('/selectedAsset', ticker);
      store.set('/modals/asset-detail-drawer', true);
    }
    if (emit) {
      emit('rowClick', row);
    }
    if (props.onRowClick) {
      props.onRowClick(row);
    }
  };

  // Helper to format values
  const renderCellContent = (col: DataTableColumn, row: any, isSubRow = false) => {
    const val = row[col.field];

    if (col.field === 'isSingleton') {
      const isSingleton = row.isSingleton;
      const hasAlts = Array.isArray(row.alternatives) && row.alternatives.length > 0;
      if (isSubRow) {
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-secondary text-secondary-foreground">
            ↳ 替代
          </span>
        );
      }
      if (isSingleton) {
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-muted text-muted-foreground">
            單兵獨立
          </span>
        );
      }
      return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-amber-500/15 text-amber-600 dark:text-amber-400">
          👑 領頭羊 {hasAlts && `(${row.alternatives.length} 替代)`}
        </span>
      );
    }

    if (col.field === 'classRank') {
      if (val === undefined || val === null || val === '') {
        return <span className="text-muted-foreground text-xs">--</span>;
      }
      return <span className="font-mono font-semibold text-foreground">#{val}</span>;
    }

    if (col.field === 'name') {
      const full = String(val || '');
      return (
        <span
          className="max-w-[130px] md:max-w-[160px] truncate block font-medium"
          title={full}
        >
          {full || '--'}
        </span>
      );
    }

    if (col.field === 'orthogonalStatus') {
      const isSeed = row.classRank === 1 || val === 'SEED' || val === '錨定種子';
      if (isSeed) {
        return (
          <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-bold bg-primary text-primary-foreground">
            ⚓ 錨定種子
          </span>
        );
      }
      const isAccepted = val === 'ACCEPTED' || val === 'ORTHOGONAL' || val === '正交合規' || val === '合規';
      const isRejected = val === 'REJECTED_COLLINEAR' || val === '共線剔除';
      const label = isAccepted ? '正交合規' : isRejected ? '共線剔除' : (val || '正交合規');
      return (
        <span
          className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
            isAccepted
              ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400'
              : 'bg-rose-500/15 text-rose-600 dark:text-rose-400'
          }`}
        >
          {label}
        </span>
      );
    }

    if (col.field === 'distributionFrequency') {
      const freqMap: Record<string, string> = {
        MONTHLY: '月配',
        QUARTERLY: '季配',
        SEMI_ANNUAL: '半年配',
        ANNUAL: '年配',
        NONE: '不配息',
      };
      const display = (val && freqMap[val]) ? freqMap[val] : val;
      return display ? (
        <span>{display}</span>
      ) : (
        <span className="text-muted-foreground text-xs">--</span>
      );
    }

    if (col.field === 'changePct' && typeof val === 'number') {
      const isPos = val > 0;
      const isNeg = val < 0;
      return (
        <span
          className={`font-semibold ${
            isPos
              ? 'text-emerald-600 dark:text-emerald-400'
              : isNeg
              ? 'text-rose-600 dark:text-rose-400'
              : 'text-muted-foreground'
          }`}
        >
          {isPos ? `+${val.toFixed(2)}%` : `${val.toFixed(2)}%`}
        </span>
      );
    }

    if (
      (col.field.startsWith('return') || col.field === 'compositeScore') &&
      typeof val === 'number'
    ) {
      return <span>{val.toFixed(2)}</span>;
    }

    if (col.field === 'fundSizeTwd' && typeof val === 'number') {
      return <span>{(val / 100000000).toFixed(1)} 億</span>;
    }

    if (val === null || val === undefined || val === '') {
      return <span className="text-muted-foreground text-xs">--</span>;
    }

    return String(val);
  };

  const hasToggle = columns.some((c) => c.field === 'isSingleton' || c.field === 'clusterId');

  // Sticky columns calculation: freeze columns up to and including 'name'
  const getStickyProps = (field: string | 'toggle') => {
    const stickyOrder = hasToggle
      ? [
          { field: 'clusterId', width: 75 },
          { field: 'isSingleton', width: 105 },
          { field: 'classRank', width: 70 },
          { field: 'ticker', width: 85 },
          { field: 'name', width: 145 },
        ]
      : [
          { field: 'classRank', width: 70 },
          { field: 'orthogonalStatus', width: 95 },
          { field: 'ticker', width: 85 },
          { field: 'name', width: 145 },
        ];

    let currentLeft = 0;
    if (hasToggle) {
      if (field === 'toggle') {
        return {
          isSticky: true,
          left: 0,
          width: 40,
          isLastSticky: false,
        };
      }
      currentLeft = 40;
    }

    const activeStickyFields = stickyOrder.filter((item) =>
      columns.some((c) => c.field === item.field)
    );
    const lastActiveField =
      activeStickyFields.length > 0
        ? activeStickyFields[activeStickyFields.length - 1].field
        : null;

    for (const item of stickyOrder) {
      if (!columns.some((c) => c.field === item.field)) {
        continue;
      }
      if (item.field === field) {
        return {
          isSticky: true,
          left: currentLeft,
          width: item.width,
          isLastSticky: item.field === lastActiveField,
        };
      }
      currentLeft += item.width;
    }

    return { isSticky: false, left: 0, width: undefined, isLastSticky: false };
  };

  return (
    <div className={`w-full space-y-3 ${props.className ?? ''}`}>
      {props.label && (
        <div className="text-base font-semibold tracking-tight">{props.label}</div>
      )}

      <div className="rounded-lg border bg-card text-card-foreground shadow-sm overflow-hidden">
        <div className="overflow-auto max-h-[480px] relative">
          <table className="w-full text-left text-sm border-separate border-spacing-0">
            <thead className="sticky top-0 z-20 bg-muted/95 backdrop-blur-xs text-muted-foreground text-xs font-medium shadow-2xs">
              <tr>
                {/* Column for expand toggle if clustering table */}
                {hasToggle && (
                  <th
                    style={{ left: 0, width: 40, minWidth: 40, maxWidth: 40 }}
                    className="py-3 px-3 w-10 text-center sticky top-0 left-0 z-30 bg-muted border-b border-border"
                  >
                    折疊
                  </th>
                )}
                {columns.map((col) => {
                  const sticky = getStickyProps(col.field);
                  const stickyStyle = sticky.isSticky
                    ? {
                        left: `${sticky.left}px`,
                        minWidth: `${sticky.width}px`,
                        maxWidth: `${sticky.width}px`,
                        width: `${sticky.width}px`,
                      }
                    : {};
                  return (
                    <th
                      key={col.field}
                      style={stickyStyle}
                      className={`py-3 px-4 whitespace-nowrap sticky top-0 border-b border-border ${
                        sticky.isSticky
                          ? `z-30 bg-muted ${
                              sticky.isLastSticky
                                ? 'border-r border-border shadow-[4px_0_8px_-3px_rgba(0,0,0,0.12)]'
                                : ''
                            }`
                          : 'z-20 bg-muted/95 backdrop-blur-xs'
                      } ${col.sortable ? 'cursor-pointer select-none hover:text-foreground' : ''}`}
                      onClick={() => col.sortable && handleSort(col.field)}
                    >
                      <div className="flex items-center gap-1.5">
                        <span>{col.label}</span>
                        {col.sortable && (
                          <span className="text-xs">
                            {sortField === col.field
                              ? sortDirection === 'asc'
                                ? '▲'
                                : '▼'
                              : '⇅'}
                          </span>
                        )}
                      </div>
                    </th>
                  );
                })}
                {children && <th className="py-3 px-4 text-right sticky top-0 z-20 bg-muted/95 border-b border-border">操作</th>}
              </tr>
            </thead>
            <tbody>
              {sortedData.length === 0 ? (
                <tr>
                  <td
                    colSpan={columns.length + 2}
                    className="py-12 text-center text-muted-foreground font-medium border-b border-border"
                  >
                    (沒有資料)
                  </td>
                </tr>
              ) : (
                sortedData.map((item, idx) => {
                  // Support both flat asset objects and { leader, alternatives, clusterId }
                  const mainRow = item.leader ? { ...item.leader, ...item } : item;
                  const clusterId = item.clusterId ?? idx;
                  const rowKey = `${clusterId}-${mainRow.ticker ?? idx}`;
                  const hasAlts =
                    Array.isArray(item.alternatives) && item.alternatives.length > 0;
                  const isExpanded = !!expandedRows[rowKey];

                  return (
                    <React.Fragment key={rowKey}>
                      {/* Main / Leader Row */}
                      <tr
                        onClick={() => handleRowClick(mainRow)}
                        className="hover:bg-muted/50 cursor-pointer transition-colors group"
                      >
                        {hasToggle && (
                          <td
                            style={{ left: 0, width: 40, minWidth: 40, maxWidth: 40 }}
                            className="py-3 px-3 text-center sticky left-0 z-10 bg-card group-hover:bg-muted/60 border-b border-border transition-colors"
                            onClick={(e) => hasAlts && toggleExpand(rowKey, e)}
                          >
                            {hasAlts ? (
                              <button
                                type="button"
                                aria-label="切換折疊"
                                onClick={(e) => toggleExpand(rowKey, e)}
                                className="p-1 rounded hover:bg-muted font-mono text-xs text-muted-foreground hover:text-foreground"
                              >
                                {isExpanded ? '▼' : '▶'}
                              </button>
                            ) : (
                              <span className="text-muted-foreground text-xs">•</span>
                            )}
                          </td>
                        )}
                        {columns.map((col) => {
                          const sticky = getStickyProps(col.field);
                          const stickyStyle = sticky.isSticky
                            ? {
                                left: `${sticky.left}px`,
                                minWidth: `${sticky.width}px`,
                                maxWidth: `${sticky.width}px`,
                                width: `${sticky.width}px`,
                              }
                            : {};
                          return (
                            <td
                              key={col.field}
                              style={stickyStyle}
                              className={`py-3 px-4 whitespace-nowrap font-medium text-foreground border-b border-border ${
                                sticky.isSticky
                                  ? `sticky z-10 bg-card group-hover:bg-muted/60 transition-colors ${
                                      sticky.isLastSticky
                                        ? 'border-r border-border shadow-[4px_0_8px_-3px_rgba(0,0,0,0.12)]'
                                        : ''
                                    }`
                                  : ''
                              }`}
                            >
                              {renderCellContent(col, mainRow, false)}
                            </td>
                          );
                        })}
                        {children && (
                          <td
                            className="py-3 px-4 text-right whitespace-nowrap border-b border-border"
                            onClick={(e) => e.stopPropagation()}
                          >
                            {children}
                          </td>
                        )}
                      </tr>

                      {/* Expandable Alternative Rows */}
                      {hasAlts &&
                        isExpanded &&
                        item.alternatives.map((rawAlt: any, altIdx: number) => {
                          const alt = rawAlt?.score
                            ? {
                                ...rawAlt.score,
                                ...rawAlt,
                                rSquaredWithLeader:
                                  rawAlt.rSquaredWithLeader ?? rawAlt.rSquared,
                              }
                            : rawAlt;
                          const altKey = `${rowKey}-alt-${alt?.ticker ?? altIdx}`;
                          return (
                            <tr
                              key={altKey}
                              onClick={() => handleRowClick(alt)}
                              className="bg-muted/30 hover:bg-muted/60 cursor-pointer transition-colors text-muted-foreground hover:text-foreground group"
                            >
                              <td
                                style={{ left: 0, width: 40, minWidth: 40, maxWidth: 40 }}
                                className="py-2.5 px-3 text-center text-xs text-muted-foreground sticky left-0 z-10 bg-muted/40 group-hover:bg-muted/70 border-b border-border transition-colors"
                              >
                                ↳
                              </td>
                              {columns.map((col) => {
                                const sticky = getStickyProps(col.field);
                                const stickyStyle = sticky.isSticky
                                  ? {
                                      left: `${sticky.left}px`,
                                      minWidth: `${sticky.width}px`,
                                      maxWidth: `${sticky.width}px`,
                                      width: `${sticky.width}px`,
                                    }
                                  : {};
                                const stickyClass = sticky.isSticky
                                  ? `sticky z-10 bg-muted/40 group-hover:bg-muted/70 transition-colors ${
                                      sticky.isLastSticky
                                        ? 'border-r border-border shadow-[4px_0_8px_-3px_rgba(0,0,0,0.12)]'
                                        : ''
                                    }`
                                  : '';

                                if (col.field === 'ticker') {
                                  return (
                                    <td
                                      key={col.field}
                                      style={stickyStyle}
                                      className={`py-2.5 px-4 whitespace-nowrap font-medium text-foreground border-b border-border ${stickyClass}`}
                                    >
                                      <div className="flex items-center gap-2">
                                        <span className="text-primary font-bold">
                                          {alt.ticker}
                                        </span>
                                        {alt.rSquaredWithLeader !== undefined && (
                                          <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[11px] font-mono bg-blue-500/10 text-blue-600 dark:text-blue-400">
                                            R²: {(alt.rSquaredWithLeader * 100).toFixed(1)}%
                                          </span>
                                        )}
                                      </div>
                                    </td>
                                  );
                                }
                                if (col.field === 'clusterId') {
                                  return (
                                    <td
                                      key={col.field}
                                      style={stickyStyle}
                                      className={`py-2.5 px-4 whitespace-nowrap text-xs text-muted-foreground font-mono border-b border-border ${stickyClass}`}
                                    >
                                      族群 {clusterId}
                                    </td>
                                  );
                                }
                                return (
                                  <td
                                    key={col.field}
                                    style={stickyStyle}
                                    className={`py-2.5 px-4 whitespace-nowrap border-b border-border ${stickyClass}`}
                                  >
                                    {renderCellContent(col, alt, true)}
                                  </td>
                                );
                              })}
                              {children && <td className="py-2.5 px-4 border-b border-border" />}
                            </tr>
                          );
                        })}
                    </React.Fragment>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
