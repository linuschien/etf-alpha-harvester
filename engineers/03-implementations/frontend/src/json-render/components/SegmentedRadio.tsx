import React, { useEffect, useState } from 'react';
import { useStateStore } from '@json-render/react';

export interface SegmentedRadioProps {
  props?: any;
  element?: any;
  bindings?: Record<string, string>;
  emit?: (event: string, ...args: any[]) => void;
}

export default function SegmentedRadio({
  props: directProps,
  element,
  bindings,
  emit,
}: SegmentedRadioProps) {
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
    (props?.id ? `/filters/${props.id}` : null) ||
    (props?.name ? `/filters/${props.name}` : null);

  const [currentValue, setCurrentValue] = useState<string>(() => {
    if (bindPath && store) {
      const v = store.get(bindPath);
      if (v !== undefined) return v;
    }
    return typeof props.value === 'string'
      ? props.value
      : props.options?.[0] || '';
  });

  useEffect(() => {
    if (!bindPath || !store?.subscribe) return;
    const unsub = store.subscribe(() => {
      const v = store.get(bindPath);
      if (v !== undefined) setCurrentValue(v);
    });
    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [store, bindPath]);

  useEffect(() => {
    if (typeof props.value === 'string' && props.value && props.value !== currentValue) {
      setCurrentValue(props.value);
    }
  }, [props.value]);

  const options: string[] = props.options || [];

  const handleSelect = (opt: string) => {
    setCurrentValue(opt);
    if (bindPath && store) {
      store.set(bindPath, opt);
    }
    if (emit) emit('change', opt);
  };

  return (
    <div className={`flex flex-wrap items-center gap-2.5 py-1 ${props.className ?? ''}`}>
      {props.label && (
        <span className="text-xs font-semibold text-muted-foreground shrink-0">
          {props.label}：
        </span>
      )}
      <div className="inline-flex items-center rounded-lg bg-muted/60 p-1 border border-border shadow-xs gap-1">
        {options.map((opt) => {
          const isSelected = currentValue === opt;
          return (
            <button
              key={opt}
              type="button"
              onClick={() => handleSelect(opt)}
              className={`px-3 py-1 text-xs font-mono font-medium rounded-md transition-all cursor-pointer ${
                isSelected
                  ? 'bg-primary text-primary-foreground font-bold shadow-xs'
                  : 'text-muted-foreground hover:text-foreground hover:bg-background/60'
              }`}
            >
              {opt}
            </button>
          );
        })}
      </div>
    </div>
  );
}
