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

  const handleClick = () => {
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
        {displayValue !== undefined && displayValue !== null
          ? String(displayValue)
          : '--'}
      </div>
      {(props.change || props.sublabel) && (
        <div className="flex items-center text-xs text-muted-foreground mt-1.5 gap-2">
          {props.change && (
            <span
              className={`font-semibold ${
                props.change.startsWith('+')
                  ? 'text-emerald-600 dark:text-emerald-400'
                  : props.change.startsWith('-')
                  ? 'text-rose-600 dark:text-rose-400'
                  : 'text-muted-foreground'
              }`}
            >
              {props.change}
            </span>
          )}
          {props.sublabel && <span>{props.sublabel}</span>}
        </div>
      )}
    </div>
  );
}
