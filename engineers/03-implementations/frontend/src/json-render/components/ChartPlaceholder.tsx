import React from 'react';

export interface ChartComponentProps {
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

export function ChartPlaceholder(chartType: string) {
  const ChartComponent = ({ element, props: directProps }: ChartComponentProps) => {
    const props = element?.props ?? directProps ?? {};
    const label = props.label ?? `${chartType} 圖表視覺化`;

    return (
      <div
        className={`w-full min-h-[220px] rounded-lg border border-dashed border-border bg-muted/20 p-6 flex flex-col items-center justify-center text-center gap-2 ${
          props.className ?? ''
        }`}
      >
        <div className="text-2xl font-mono text-muted-foreground/60">
          📊 [{chartType}]
        </div>
        <div className="text-sm font-medium text-foreground">{label}</div>
        {props.data_ref && (
          <div className="text-xs font-mono text-muted-foreground">
            數據來源: {props.data_ref}
          </div>
        )}
      </div>
    );
  };

  ChartComponent.displayName = `ChartPlaceholder(${chartType})`;
  return ChartComponent;
}

export default ChartPlaceholder;
