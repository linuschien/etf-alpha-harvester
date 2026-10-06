import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { JSONUIProvider, createStateStore } from '@json-render/react';
import { describe, it, expect, vi } from 'vitest';
import AlertDialog from './AlertDialog';
import Breadcrumb from './Breadcrumb';
import MetricCard from './MetricCard';
import ChartComponent from './ChartComponent';
import ChartPlaceholder from './ChartPlaceholder';
import DataTable from './DataTable';
import { api } from '@/lib/api-client';

describe('Custom JSON-render Components', () => {
  it('renders and interacts with AlertDialog', async () => {
    const user = userEvent.setup();
    const store = createStateStore({ modals: { 'test-dialog': true } });
    const onConfirm = vi.fn();
    const onCancel = vi.fn();

    render(
      <JSONUIProvider store={store} registry={{}}>
        <AlertDialog
          props={{
            id: 'test-dialog',
            title: '測試確認框',
            description: '確認要執行此操作？',
            onConfirm,
            onCancel,
          }}
        />
      </JSONUIProvider>
    );

    expect(screen.getByText('測試確認框')).toBeInTheDocument();
    expect(screen.getByText('確認要執行此操作？')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /確認/i }));
    expect(onConfirm).toHaveBeenCalled();
    expect(store.get('/modals/test-dialog')).toBe(false);
  });

  it('handles cancel in AlertDialog', async () => {
    const user = userEvent.setup();
    const store = createStateStore({ modals: { 'test-cancel-dialog': true } });
    const onCancel = vi.fn();

    render(
      <JSONUIProvider store={store} registry={{}}>
        <AlertDialog
          props={{
            id: 'test-cancel-dialog',
            title: '取消對話框',
            onCancel,
          }}
        />
      </JSONUIProvider>
    );

    await user.click(screen.getByRole('button', { name: /取消/i }));
    expect(store.get('/modals/test-cancel-dialog')).toBe(false);
  });

  it('renders Breadcrumb correctly', () => {
    render(
      <Breadcrumb
        props={{
          items: [
            { label: '首頁', href: '/' },
            { label: '情報中心', href: '/intelligence' },
            { label: '全球天梯' },
          ],
        }}
      />
    );

    expect(screen.getByText('首頁')).toBeInTheDocument();
    expect(screen.getByText('情報中心')).toBeInTheDocument();
    expect(screen.getByText('全球天梯')).toBeInTheDocument();
  });

  it('renders and handles MetricCard interactions', async () => {
    const user = userEvent.setup();
    const store = createStateStore({ metrics: { 'kpi-score': '95.5' } });
    const onClick = vi.fn();

    render(
      <JSONUIProvider store={store} registry={{}}>
        <MetricCard
          props={{
            id: 'kpi-score',
            label: '綜合評分',
            change: '+2.5%',
            sublabel: '優於 92% 標的',
            onClick,
          }}
        />
      </JSONUIProvider>
    );

    expect(screen.getByText('綜合評分')).toBeInTheDocument();
    expect(screen.getByText('95.5')).toBeInTheDocument();
    expect(screen.getByText('+2.5%')).toBeInTheDocument();
    expect(screen.getByText('優於 92% 標的')).toBeInTheDocument();

    await user.click(screen.getByText('綜合評分'));
    expect(onClick).toHaveBeenCalled();
  });

  it('renders ChartPlaceholder properly', () => {
    const BarChart = ChartPlaceholder('Bar');
    render(
      <BarChart
        props={{
          id: 'test-chart',
          label: '柱狀分析圖',
          data_ref: 'getQuoteData',
        }}
      />
    );

    expect(screen.getByText(/\[Bar\]/i)).toBeInTheDocument();
    expect(screen.getByText('柱狀分析圖')).toBeInTheDocument();
    expect(screen.getByText(/數據來源: getQuoteData/i)).toBeInTheDocument();
  });

  it('tests DataTable sorting and singleton rendering', async () => {
    const user = userEvent.setup();
    const rows = [
      {
        clusterId: 2,
        ticker: '0056',
        name: '元大高股息',
        compositeScore: 84.1,
        isSingleton: true,
      },
      {
        clusterId: 1,
        ticker: '0050',
        name: '元大台灣50',
        compositeScore: 92.4,
        isSingleton: false,
      },
    ];

    render(
      <DataTable
        props={{
          id: 'test-table',
          label: '測試表格',
          columns: [
            { field: 'clusterId', label: '編號', sortable: true },
            { field: 'ticker', label: '代碼', sortable: true },
            { field: 'isSingleton', label: '角色' },
            { field: 'compositeScore', label: '評分', sortable: true },
          ],
          data: rows,
        }}
      />
    );

    expect(screen.getByText('測試表格')).toBeInTheDocument();
    expect(screen.getByText('單兵獨立')).toBeInTheDocument();
    expect(screen.getByText('👑 領頭羊')).toBeInTheDocument();

    // Click to sort by compositeScore
    const scoreHeader = screen.getByText('評分');
    await user.click(scoreHeader);
    expect(scoreHeader).toBeInTheDocument();
  });

  it('renders ChartComponent with panic sparkline channel thresholds and status', () => {
    const store = createStateStore({
      data: {
        quoteTimeSeries: {
          fearGreed: [
            { tradeDate: '2026-09-01', closePrice: 85 },
            { tradeDate: '2026-09-02', closePrice: 20 },
          ],
          move: [
            { tradeDate: '2026-09-01', closePrice: 50 },
            { tradeDate: '2026-09-02', closePrice: 130 },
          ],
        },
      },
    });

    render(
      <JSONUIProvider store={store} registry={{}}>
        <ChartComponent props={{ id: 'chart-fear-greed-spark' }} />
        <ChartComponent props={{ id: 'chart-move-spark' }} />
      </JSONUIProvider>
    );

    expect(screen.getByText('FEAR_GREED 恐懼貪婪指數 (CNN)')).toBeInTheDocument();
    expect(screen.getByText('^MOVE 美債波動率指數 (美債期權)')).toBeInTheDocument();
    expect(screen.getAllByText(/⚠️ 過度恐慌/i)).toHaveLength(2);
  });
});
