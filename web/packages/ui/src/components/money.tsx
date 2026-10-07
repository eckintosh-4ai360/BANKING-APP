import { cn } from '../lib/cn';
import { formatMoney, isNegativeAmount, type MoneyFormatOptions } from '../lib/format';

export interface MoneyProps extends MoneyFormatOptions {
  /** Decimal string from the backend, e.g. "1250.00". Numbers are deliberately not accepted. */
  amount: string | null | undefined;
  className?: string;
}

/** Renders a server-calculated amount. The UI only displays money; it never computes it. */
export function Money({ amount, className, ...options }: MoneyProps) {
  return (
    <span className={cn('tabular-nums', isNegativeAmount(amount) && 'text-destructive', className)}>
      {formatMoney(amount, options)}
    </span>
  );
}
