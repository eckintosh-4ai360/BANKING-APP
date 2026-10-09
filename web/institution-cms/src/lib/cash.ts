/**
 * Notes and coins by currency, largest first. Anything else falls back to the cedi set (the backend accepts any
 * positive denomination; this list only shapes the counting form).
 */
const CEDI = ['200', '100', '50', '20', '10', '5', '2', '1', '0.50', '0.20', '0.10', '0.05', '0.01'];
const DENOMINATIONS: Record<string, string[]> = {
  GHS: CEDI,
  USD: ['100', '50', '20', '10', '5', '2', '1', '0.25', '0.10', '0.05', '0.01'],
  EUR: ['500', '200', '100', '50', '20', '10', '5', '2', '1', '0.50', '0.20', '0.10', '0.05', '0.02', '0.01'],
  GBP: ['50', '20', '10', '5', '2', '1', '0.50', '0.20', '0.10', '0.05', '0.02', '0.01'],
};

export function denominationsOf(currency: string): string[] {
  return DENOMINATIONS[currency] ?? CEDI;
}

/** Pieces counted per denomination, as typed (empty means none). */
export type CountInput = Record<string, string>;

const PIECES = /^(0|[1-9][0-9]{0,7})$/;
const SCALE = 4;

/** Whether every typed number of pieces is a whole number (blank counts as zero). */
export function isValidCount(input: CountInput): boolean {
  return Object.values(input).every((pieces) => pieces.trim() === '' || PIECES.test(pieces.trim()));
}

/** The count to send: denominations with at least one piece. The server computes the total itself. */
export function toCashCount(input: CountInput): { denominations: Record<string, number> } {
  const denominations: Record<string, number> = {};
  for (const [denomination, pieces] of Object.entries(input)) {
    const value = pieces.trim();
    if (value !== '' && PIECES.test(value) && Number(value) > 0) {
      denominations[denomination] = Number(value);
    }
  }
  return { denominations };
}

/**
 * Total of a count, exactly (integer arithmetic in ten-thousandths, never floating point), for display while
 * counting. The authoritative total is the server's.
 */
export function countTotal(input: CountInput, minorUnits = 2): string {
  let total = 0n;
  for (const [denomination, pieces] of Object.entries(toCashCount(input).denominations)) {
    total += toUnits(denomination) * BigInt(pieces);
  }
  return format(total, minorUnits);
}

function toUnits(decimal: string): bigint {
  const [whole, fraction = ''] = decimal.split('.');
  return BigInt(whole ?? '0') * 10n ** BigInt(SCALE) + BigInt((fraction + '0'.repeat(SCALE)).slice(0, SCALE));
}

function format(units: bigint, minorUnits: number): string {
  const divisor = 10n ** BigInt(SCALE);
  const whole = units / divisor;
  const fraction = (units % divisor).toString().padStart(SCALE, '0');
  const shown = fraction.slice(0, Math.max(minorUnits, fraction.replace(/0+$/, '').length));
  return shown.length > 0 ? `${whole}.${shown}` : whole.toString();
}
