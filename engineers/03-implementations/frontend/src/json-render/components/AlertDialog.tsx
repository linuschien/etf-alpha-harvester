import React from 'react';
import { useStateStore } from '@json-render/react';

export interface AlertDialogProps {
  id?: string;
  title?: string;
  description?: string;
  openPath?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  onConfirm?: () => void;
  onCancel?: () => void;
  className?: string;
}

export default function AlertDialog({
  element,
  props: directProps,
  children,
  emit,
}: any) {
  const props: AlertDialogProps = element?.props ?? directProps ?? {};

  let store: any = null;
  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  const openPath = props.openPath ?? (props.id ? `/modals/${props.id}` : null);
  const isOpen = openPath && store ? Boolean(store.get(openPath)) : true;

  if (!isOpen) return null;

  const handleClose = () => {
    if (openPath && store) {
      store.set(openPath, false);
    }
    if (emit) emit('cancel');
  };

  const handleConfirm = () => {
    if (openPath && store) {
      store.set(openPath, false);
    }
    if (emit) emit('confirm');
    if (props.onConfirm) props.onConfirm();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4"
    >
      <div className="w-full max-w-md rounded-xl border bg-card p-6 shadow-lg space-y-4 animate-in fade-in-0 zoom-in-95">
        <div className="space-y-1.5">
          <h3 className="text-lg font-semibold tracking-tight">{props.title ?? '確認操作'}</h3>
          {props.description && (
            <p className="text-sm text-muted-foreground">{props.description}</p>
          )}
        </div>

        {children}

        <div className="flex justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={handleClose}
            className="px-4 py-2 text-sm font-medium rounded-md border border-input bg-background hover:bg-muted text-foreground transition-colors"
          >
            {props.cancelLabel ?? '取消'}
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            className="px-4 py-2 text-sm font-medium rounded-md bg-destructive text-destructive-foreground hover:bg-destructive/90 transition-colors"
          >
            {props.confirmLabel ?? '確認'}
          </button>
        </div>
      </div>
    </div>
  );
}
