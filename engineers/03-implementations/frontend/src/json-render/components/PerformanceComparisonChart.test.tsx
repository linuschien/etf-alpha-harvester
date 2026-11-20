import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { JSONUIProvider, createStateStore } from '@json-render/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '@/mocks/server';
import { componentRegistry } from '../component-registry';
import PerformanceComparisonChart from './PerformanceComparisonChart';

function createTestStore() {
  return createStateStore({
    modals: {},
    form: {},
    data: {
      listDataFeedWatermarks: [
        { feedName: 'TAIWAN_ETF_QUOTES', latestRecordDate: '2026-09-30', status: 'SUCCESS' },
      ],
    },
    filters: {
      'performance-compare-tickers': [],
      'performance-compare-window': '6M',
    },
  });
}

function renderCompareChart(store = createTestStore()) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return {
    store,
    ...render(
      <QueryClientProvider client={qc}>
        <JSONUIProvider registry={componentRegistry} store={store} handlers={{}}>
          <PerformanceComparisonChart />
        </JSONUIProvider>
      </QueryClientProvider>
    ),
  };
}

describe('PerformanceComparisonChart Component (US-G02-05)', () => {
  beforeEach(() => {
    server.use(
      http.post('/graphql', async ({ request }) => {
        const body: any = await request.json();
        const query = String(body?.query || '');

        if (query.includes('listGlobalAssets')) {
          return HttpResponse.json({
            data: {
              listGlobalAssets: [
                { ticker: '0050', name: '元大台灣50', listingDate: '2003-06-30', netAssetValue: 190, fundSizeTwd: 4e11 },
                { ticker: '00757', name: '統一FANG+', listingDate: '2018-12-06', netAssetValue: 98.5, fundSizeTwd: 2.8e10 },
                { ticker: '00999', name: '測試新上市ETF', listingDate: '2026-08-15', netAssetValue: 15.2, fundSizeTwd: 5e9 },
                { ticker: '0054', name: '已下市ETF', listingDate: '2007-07-16', netAssetValue: null, fundSizeTwd: null },
              ],
            },
          });
        }

        if (query.includes('getQuoteTimeSeries')) {
          const ticker = body?.variables?.ticker;
          if (ticker === '0050') {
            return HttpResponse.json({
              data: {
                getQuoteTimeSeries: [
                  { ticker: '0050', tradeDate: '2026-03-30', closePrice: 100 },
                  { ticker: '0050', tradeDate: '2026-07-21', closePrice: 100 },
                  { ticker: '0050', tradeDate: '2026-08-28', closePrice: 110 },
                  { ticker: '0050', tradeDate: '2026-09-30', closePrice: 110 },
                ],
              },
            });
          }
          if (ticker === '00757') {
            return HttpResponse.json({
              data: {
                getQuoteTimeSeries: [
                  { ticker: '00757', tradeDate: '2026-03-30', closePrice: 80 },
                  { ticker: '00757', tradeDate: '2026-07-21', closePrice: 96 },
                  { ticker: '00757', tradeDate: '2026-08-28', closePrice: 100 },
                  { ticker: '00757', tradeDate: '2026-09-30', closePrice: 112 },
                ],
              },
            });
          }
          if (ticker === '00999') {
            return HttpResponse.json({
              data: {
                getQuoteTimeSeries: [
                  { ticker: '00999', tradeDate: '2026-08-28', closePrice: 15 },
                  { ticker: '00999', tradeDate: '2026-09-30', closePrice: 16.5 },
                ],
              },
            });
          }
          return HttpResponse.json({
            data: {
              getQuoteTimeSeries: [
                { ticker, tradeDate: '2026-03-30', closePrice: 20000 },
                { ticker, tradeDate: '2026-08-28', closePrice: 22000 },
                { ticker, tradeDate: '2026-09-30', closePrice: 23000 },
              ],
            },
          });
        }

        if (query.includes('listDividendAnnouncements')) {
          const ticker = body?.variables?.filter?.ticker;
          if (ticker === '0050') {
            return HttpResponse.json({
              data: {
                listDividendAnnouncements: [
                  { ticker: '0050', exDate: '2026-07-21', dividendPerShare: 10 },
                ],
              },
            });
          }
          return HttpResponse.json({ data: { listDividendAnnouncements: [] } });
        }

        return HttpResponse.json({ data: {} });
      })
    );
  });

  it('renders empty state by default with 0 selected assets, 4 standard windows, no preset buttons, and no summary table', async () => {
    renderCompareChart();

    expect(await screen.findByText('尚未挑選任何比較標的')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '1M' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '3M' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '6M' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '1Y' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /清除全部/ })).toBeDisabled();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('excludes delisted ETFs from combobox while showing global benchmarks and active ETFs, plots curves, and displays final compound return on Chips', async () => {
    const user = userEvent.setup();
    const { store } = renderCompareChart();

    const combobox = screen.getByRole('combobox', { name: '新增比較標的' });
    await user.click(combobox);

    // Wait for listGlobalAssets to load into dropdown
    const listbox = await screen.findByRole('listbox');
    expect(within(listbox).getByText('^TWII')).toBeInTheDocument();
    expect(within(listbox).getByText('0050')).toBeInTheDocument();
    expect(within(listbox).getByText('00999')).toBeInTheDocument();
    // Delisted 0054 must NOT be listed
    expect(within(listbox).queryByText('0054')).not.toBeInTheDocument();

    // Select 0050 (has dividend 10 on 07-21 at close 100 -> 1.1 shares * 110 = 121 -> +21.00%)
    await user.click(within(listbox).getByRole('option', { name: /0050/ }));

    // Select 00757 (80 -> 112 = +40.00%)
    await user.click(combobox);
    const listbox2 = await screen.findByRole('listbox');
    await user.click(within(listbox2).getByRole('option', { name: /00757/ }));

    // Verify chips show final compound total returns directly
    const chip0050 = await screen.findByTestId('compare-chip-0050');
    await waitFor(() => {
      expect(chip0050).toHaveAttribute('data-status', 'ready');
      expect(chip0050).toHaveTextContent('+21.00%');
    });

    const chip00757 = await screen.findByTestId('compare-chip-00757');
    await waitFor(() => {
      expect(chip00757).toHaveAttribute('data-status', 'ready');
      expect(chip00757).toHaveTextContent('+40.00%');
    });

    // Verify SVG chart is rendered with extended height >= 520px and both series plotted
    const chartSvg = screen.getByRole('img', { name: '多標的同起點含息累積報酬率比較折線圖' });
    expect(chartSvg).toBeInTheDocument();
    expect(parseInt(chartSvg.style.height, 10)).toBeGreaterThanOrEqual(520);
    expect(screen.getByTestId('compare-series-0050')).toBeInTheDocument();
    expect(screen.getByTestId('compare-series-00757')).toBeInTheDocument();

    // Verify store persisted selected tickers
    expect(store.get('/filters/performance-compare-tickers')).toEqual(['0050', '00757']);

    // Clear all resets back to empty state
    await user.click(screen.getByRole('button', { name: /清除全部/ }));
    expect(await screen.findByText('尚未挑選任何比較標的')).toBeInTheDocument();
    expect(store.get('/filters/performance-compare-tickers')).toEqual([]);
  });

  it('shows dimmed grayscale Chip for newly listed ETF in 6M and automatically restores its line when switching to 1M', async () => {
    const user = userEvent.setup();
    renderCompareChart();

    const combobox = screen.getByRole('combobox', { name: '新增比較標的' });
    await user.click(combobox);
    const listbox = await screen.findByRole('listbox');
    await user.click(within(listbox).getByRole('option', { name: /00999/ }));

    // In default 6M window, 00999 (listed 2026-08-15) is dimmed and NOT plotted
    const chip999 = await screen.findByTestId('compare-chip-00999');
    await waitFor(() => {
      expect(chip999).toHaveAttribute('data-status', 'insufficient_history');
      expect(chip999).toHaveTextContent('上市未滿 6M，暫不顯示');
    });
    expect(screen.queryByTestId('compare-series-00999')).not.toBeInTheDocument();
    expect(screen.getByText(/所選標的上市時間皆未滿此區間/)).toBeInTheDocument();

    // Switch to 1M -> 00999 automatically becomes ready (+10.00%) and plots its line!
    await user.click(screen.getByRole('button', { name: '1M' }));
    await waitFor(() => {
      expect(screen.getByTestId('compare-chip-00999')).toHaveAttribute('data-status', 'ready');
      expect(screen.getByTestId('compare-chip-00999')).toHaveTextContent('+10.00%');
      expect(screen.getByTestId('compare-series-00999')).toBeInTheDocument();
    });
  });

  it('disables search input when 10 tickers are selected and allows removing a chip to re-enable', async () => {
    const user = userEvent.setup();
    const store = createTestStore();
    store.set('/filters/performance-compare-tickers', [
      '^TWII',
      '^GSPC',
      '^NDX',
      '^SOX',
      '^N225',
      '0050',
      '00757',
      '00830',
      '006208',
      '0056',
    ]);

    renderCompareChart(store);

    const combobox = screen.getByRole('combobox', { name: '新增比較標的' });
    expect(combobox).toBeDisabled();
    expect(combobox).toHaveAttribute('placeholder', expect.stringContaining('已達上限 (10 / 10 檔)'));

    // Remove one chip -> input becomes enabled again
    await user.click(screen.getByRole('button', { name: '移除 0056' }));
    expect(combobox).not.toBeDisabled();
    expect(store.get('/filters/performance-compare-tickers')).toHaveLength(9);
  });
});
