import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api-client';
import { getRollingDateRange } from '@/lib/date-utils';
import { MarketDailyQuote } from './use-get-quote-time-series';

export interface BenchmarkQuotesData {
  twii: MarketDailyQuote[];
  gspc: MarketDailyQuote[];
  ndx: MarketDailyQuote[];
  sox: MarketDailyQuote[];
  n225: MarketDailyQuote[];
}

export interface PanicQuotesData {
  vix: MarketDailyQuote[];
  vxn: MarketDailyQuote[];
  fearGreed: MarketDailyQuote[];
  move: MarketDailyQuote[];
}

export const benchmarkQuotesKeys = {
  all: ['benchmarkQuotes'] as const,
  window: (startDate: string, endDate: string) =>
    ['benchmarkQuotes', startDate, endDate] as const,
};

export const panicQuotesKeys = {
  all: ['panicQuotes'] as const,
  window: (startDate: string, endDate: string) =>
    ['panicQuotes', startDate, endDate] as const,
};

export function useBenchmarkQuotes(
  startDate?: string,
  endDate?: string,
  options?: { enabled?: boolean }
) {
  const defaultRange = getRollingDateRange(1);
  const qStart = startDate ?? defaultRange.startDate;
  const qEnd = endDate ?? defaultRange.endDate;

  return useQuery({
    queryKey: benchmarkQuotesKeys.window(qStart, qEnd),
    enabled: options?.enabled,
    queryFn: () =>
      api
        .graphql<BenchmarkQuotesData>(
          `query GetBenchmarkQuotes($startDate: String!, $endDate: String!) {
            twii: getQuoteTimeSeries(ticker: "^TWII", startDate: $startDate, endDate: $endDate) {
              ticker tradeDate openPrice highPrice lowPrice closePrice ma20 ma60 ma120 ma240 bbUpper bbMiddle bbLower
            }
            gspc: getQuoteTimeSeries(ticker: "^GSPC", startDate: $startDate, endDate: $endDate) {
              ticker tradeDate openPrice highPrice lowPrice closePrice ma20 ma60 ma120 ma240 bbUpper bbMiddle bbLower
            }
            ndx: getQuoteTimeSeries(ticker: "^NDX", startDate: $startDate, endDate: $endDate) {
              ticker tradeDate openPrice highPrice lowPrice closePrice ma20 ma60 ma120 ma240 bbUpper bbMiddle bbLower
            }
            sox: getQuoteTimeSeries(ticker: "^SOX", startDate: $startDate, endDate: $endDate) {
              ticker tradeDate openPrice highPrice lowPrice closePrice ma20 ma60 ma120 ma240 bbUpper bbMiddle bbLower
            }
            n225: getQuoteTimeSeries(ticker: "^N225", startDate: $startDate, endDate: $endDate) {
              ticker tradeDate openPrice highPrice lowPrice closePrice ma20 ma60 ma120 ma240 bbUpper bbMiddle bbLower
            }
          }`,
          { startDate: qStart, endDate: qEnd }
        )
        .then((data) => data),
  });
}

export function usePanicQuotes(
  startDate?: string,
  endDate?: string,
  options?: { enabled?: boolean }
) {
  const defaultRange = getRollingDateRange(1);
  const qStart = startDate ?? defaultRange.startDate;
  const qEnd = endDate ?? defaultRange.endDate;

  return useQuery({
    queryKey: panicQuotesKeys.window(qStart, qEnd),
    enabled: options?.enabled,
    queryFn: () =>
      api
        .graphql<PanicQuotesData>(
          `query GetPanicQuotes($startDate: String!, $endDate: String!) {
            vix: getQuoteTimeSeries(ticker: "^VIX", startDate: $startDate, endDate: $endDate) {
              ticker tradeDate openPrice highPrice lowPrice closePrice
            }
            vxn: getQuoteTimeSeries(ticker: "^VXN", startDate: $startDate, endDate: $endDate) {
              ticker tradeDate openPrice highPrice lowPrice closePrice
            }
            fearGreed: getQuoteTimeSeries(ticker: "FEAR_GREED", startDate: $startDate, endDate: $endDate) {
              ticker tradeDate openPrice highPrice lowPrice closePrice
            }
            move: getQuoteTimeSeries(ticker: "^MOVE", startDate: $startDate, endDate: $endDate) {
              ticker tradeDate openPrice highPrice lowPrice closePrice
            }
          }`,
          { startDate: qStart, endDate: qEnd }
        )
        .then((data) => data),
  });
}
