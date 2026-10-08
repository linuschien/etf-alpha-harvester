import React from 'react';
import { Drawer as VaulDrawer } from 'vaul';
import { useStateStore } from '@json-render/react';

export interface DrawerProps {
  id?: string;
  title?: string;
  description?: string;
  openPath?: string;
  className?: string;
}

export default function Drawer({
  element,
  props: directProps,
  children,
  emit,
}: any) {
  const props: DrawerProps = element?.props ?? directProps ?? {};

  let store: any = null;
  try {
    store = useStateStore();
  } catch {
    store = null;
  }

  const [, setTick] = React.useState(0);
  React.useEffect(() => {
    if (!store?.subscribe) return;
    const unsub = store.subscribe(() => {
      setTick((t) => t + 1);
    });
    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [store]);

  const openPath = props.openPath ?? (props.id ? `/modals/${props.id}` : null);
  const isOpen = openPath && store ? Boolean(store.get(openPath)) : true;

  const handleOpenChange = (open: boolean) => {
    if (openPath && store) {
      store.set(openPath, open);
    }
    if (!open && emit) {
      emit('close');
    }
  };

  return (
    <VaulDrawer.Root
      open={isOpen}
      onOpenChange={handleOpenChange}
      shouldScaleBackground={false}
    >
      <VaulDrawer.Portal>
        <VaulDrawer.Overlay className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs transition-opacity duration-200" />
        <VaulDrawer.Content
          data-slot="drawer-content"
          className="fixed inset-x-0 bottom-0 z-50 mt-12 flex max-h-[88vh] flex-col rounded-t-2xl border border-border bg-background shadow-2xl focus:outline-hidden"
        >
          {/* Top Drag Handle */}
          <div className="mx-auto mt-3 h-1.5 w-16 shrink-0 rounded-full bg-muted-foreground/30 hover:bg-muted-foreground/50 transition-colors" />

          {/* Accessible Title / Description for Vaul compliance */}
          <VaulDrawer.Title className="sr-only">
            {props.title ?? '詳細資訊'}
          </VaulDrawer.Title>
          {props.description && (
            <VaulDrawer.Description className="sr-only">
              {props.description}
            </VaulDrawer.Description>
          )}

          {/* Scrollable Body: all decision modules (Radar, K-Line, Dip-Buy) render here smoothly */}
          <div className="flex-1 overflow-y-auto px-4 sm:px-6 py-4 space-y-6 max-w-5xl w-full mx-auto overscroll-contain">
            {children}
          </div>
        </VaulDrawer.Content>
      </VaulDrawer.Portal>
    </VaulDrawer.Root>
  );
}

