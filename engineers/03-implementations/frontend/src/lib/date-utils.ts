/**
 * Date utility functions for dynamic rolling windows.
 * Formats dates as YYYY-MM-DD and computes dynamic lookback ranges.
 */

/**
 * Format a Date object to YYYY-MM-DD string using local date values.
 */
export function formatDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * Returns dynamic rolling date range [startDate, endDate]
 * Default: 1-year lookback ending today (or referenceDate)
 */
export function getRollingDateRange(
  lookbackYears = 1,
  referenceDate: Date = new Date()
): { startDate: string; endDate: string } {
  const endDate = formatDate(referenceDate);
  const start = new Date(referenceDate);
  start.setFullYear(start.getFullYear() - lookbackYears);
  const startDate = formatDate(start);
  return { startDate, endDate };
}

/**
 * Returns recent days rolling date range [startDate, endDate]
 * Default: 5 days lookback ending today (or referenceDate)
 */
export function getRecentDaysRange(
  days = 5,
  referenceDate: Date = new Date()
): { startDate: string; endDate: string } {
  const endDate = formatDate(referenceDate);
  const start = new Date(referenceDate);
  start.setDate(start.getDate() - days);
  const startDate = formatDate(start);
  return { startDate, endDate };
}
