import { describe, it, expect } from 'vitest';
import { formatDate, getRollingDateRange, getRecentDaysRange } from './date-utils';

describe('date-utils', () => {
  it('formats Date to YYYY-MM-DD correctly', () => {
    const d = new Date(2026, 9, 5); // Month is 0-indexed: 9 = October
    expect(formatDate(d)).toBe('2026-10-05');
  });

  it('calculates 1-year rolling date range correctly', () => {
    const ref = new Date(2026, 9, 5);
    const range = getRollingDateRange(1, ref);
    expect(range.endDate).toBe('2026-10-05');
    expect(range.startDate).toBe('2025-10-05');
  });

  it('calculates recent days range correctly', () => {
    const ref = new Date(2026, 9, 5);
    const range = getRecentDaysRange(5, ref);
    expect(range.endDate).toBe('2026-10-05');
    expect(range.startDate).toBe('2026-09-30');
  });
});
