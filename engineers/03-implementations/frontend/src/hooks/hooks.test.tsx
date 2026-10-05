import React from 'react';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect } from 'vitest';

import { useGetDipBuyOpportunity } from './use-get-dip-buy-opportunity';
import { useGetGlobalAssetByTicker } from './use-get-global-asset-by-ticker';
import { useGetQuoteTimeSeries } from './use-get-quote-time-series';
import { useGetScoreByTicker } from './use-get-score-by-ticker';
import { useListMarketDailyQuotes } from './use-list-market-daily-quotes';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('API Hook Stubs', () => {
  it('useGetDipBuyOpportunity queries data correctly', async () => {
    const { result } = renderHook(() => useGetDipBuyOpportunity('0050'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.ticker).toBe('0050');
  });

  it('useGetGlobalAssetByTicker queries asset metadata', async () => {
    const { result } = renderHook(() => useGetGlobalAssetByTicker('0050'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.name).toBe('元大台灣50');
  });

  it('useGetQuoteTimeSeries queries quotes', async () => {
    const { result } = renderHook(
      () => useGetQuoteTimeSeries('0050', '2026-09-01', '2026-09-30'),
      { wrapper: createWrapper() }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useGetScoreByTicker queries scores', async () => {
    const { result } = renderHook(
      () => useGetScoreByTicker('0050', '2026-09-23'),
      { wrapper: createWrapper() }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it('useListMarketDailyQuotes queries daily quotes', async () => {
    const { result } = renderHook(() => useListMarketDailyQuotes(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});
