/**
 * Display formatting. Money arrives from the backend as a decimal string and is formatted from that string directly:
 * it is never converted to a JavaScript number, so large balances keep every digit and the UI never does arithmetic on
 * amounts.
 */

const DECIMAL = /^[+-]?\d+(\.\d+)?$/;

export interface MoneyFormatOptions {
  currency: string;
  locale?: string;
  /** Show the ISO code instead of the local symbol (useful in tables mixing currencies). */
  display?: 'symbol' | 'code' | 'narrowSymbol';
}

/** Formats a decimal string such as "1234567.50" as currency. Returns "—" for invalid input rather than guessing. */
export function formatMoney(amount: string | null | undefined, options: MoneyFormatOptions): string {
  if (amount === null || amount === undefined) {
    return '—';
  }
  const value = amount.trim();
  if (!DECIMAL.test(value)) {
    return '—';
  }
  const formatter = new Intl.NumberFormat(options.locale ?? 'en-GH', {
    style: 'currency',
    currency: options.currency,
    currencyDisplay: options.display ?? 'symbol',
  });
  // Intl.NumberFormat formats numeric strings as exact decimals (ECMA-402 NumberFormat v3).
  return formatter.format(value as `${number}`);
}

/** True when the decimal string is negative (for styling only). */
export function isNegativeAmount(amount: string | null | undefined): boolean {
  if (typeof amount !== 'string') {
    return false;
  }
  const value = amount.trim();
  return DECIMAL.test(value) && value.startsWith('-') && /[1-9]/.test(value);
}

export function formatDateTime(value: string | null | undefined, locale = 'en-GB', timeZone?: string): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short', timeZone }).format(date);
}

export function formatDate(value: string | null | undefined, locale = 'en-GB'): string {
  if (!value) {
    return '—';
  }
  // Plain dates (yyyy-MM-dd) are calendar dates, not instants: format them in UTC so no time zone shifts the day.
  const date = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? `${value}T00:00:00Z` : value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: 'UTC' }).format(date);
}

/** "PENDING_REVIEW" → "Pending review". */
export function humanize(code: string | null | undefined): string {
  if (!code) {
    return '—';
  }
  const text = code.replace(/[_.-]+/g, ' ').trim().toLowerCase();
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes < 0) {
    return '—';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
