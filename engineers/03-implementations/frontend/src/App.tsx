import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import GlobalMarketIntelligencePage from './pages/global-market-intelligence.page';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,
      retry: 1,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <GlobalMarketIntelligencePage />
    </QueryClientProvider>
  );
}
