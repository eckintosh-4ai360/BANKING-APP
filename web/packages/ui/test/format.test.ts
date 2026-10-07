import { describe, expect, it } from 'vitest';
import { formatBytes, formatDate, formatMoney, humanize, isNegativeAmount } from '../src/lib/format';

describe('formatMoney', () => {
  it('formats a decimal string as currency', () => {
    expect(formatMoney('1234.5', { currency: 'GHS', locale: 'en-GH' })).toBe('GH₵1,234.50');
  });

  it('keeps every digit of amounts beyond double precision', () => {
    // 2^53 + 1 cannot be represented as a JavaScript number; the formatter must not round it.
    expect(formatMoney('9007199254740993.01', { currency: 'GHS', locale: 'en-GH', display: 'code' }).replace(/\s/g, ' ')).toBe(
      'GHS 9,007,199,254,740,993.01',
    );
  });

  it('formats negative amounts', () => {
    expect(formatMoney('-50.00', { currency: 'USD', locale: 'en-US' })).toBe('-$50.00');
  });

  it.each([null, undefined, '', 'abc', '1e5', '12.3.4', 'NaN', '1,000.00'])('refuses to guess for %s', (input) => {
    expect(formatMoney(input as string | null | undefined, { currency: 'GHS' })).toBe('—');
  });
});

describe('isNegativeAmount', () => {
  it.each([
    ['-0.01', true],
    ['-10', true],
    ['0', false],
    ['-0.00', false],
    ['15.00', false],
    ['garbage', false],
  ])('%s → %s', (input, expected) => {
    expect(isNegativeAmount(input)).toBe(expected);
  });
});

describe('formatDate', () => {
  it('does not shift calendar dates across time zones', () => {
    expect(formatDate('1990-01-01', 'en-GB')).toBe('1 Jan 1990');
  });

  it('returns a dash for missing or invalid dates', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate('not-a-date')).toBe('—');
  });
});

describe('humanize and formatBytes', () => {
  it('turns codes into labels', () => {
    expect(humanize('PENDING_REVIEW')).toBe('Pending review');
    expect(humanize('customer.view')).toBe('Customer view');
    expect(humanize(undefined)).toBe('—');
  });

  it('formats file sizes', () => {
    expect(formatBytes(512)).toBe('512 B');
    expect(formatBytes(2048)).toBe('2.0 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
  });
});
