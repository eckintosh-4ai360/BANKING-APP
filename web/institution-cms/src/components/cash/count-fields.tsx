'use client';

import { Input, Money } from '@banking/ui';
import { countTotal, denominationsOf, type CountInput } from '@/lib/cash';

/**
 * Note-by-note cash count. Only pieces are entered; the total shown is for the counter's convenience and the server
 * computes its own.
 */
export function CountFields({
  currency,
  value,
  onChange,
  idPrefix,
}: {
  currency: string;
  value: CountInput;
  onChange: (value: CountInput) => void;
  idPrefix: string;
}) {
  return (
    <fieldset className="grid gap-3">
      <legend className="mb-2 text-sm font-medium">Pieces counted</legend>
      <div className="grid grid-cols-2 gap-x-4 gap-y-2 sm:grid-cols-3">
        {denominationsOf(currency).map((denomination) => {
          const id = `${idPrefix}-${denomination.replace('.', '_')}`;
          return (
            <label key={denomination} htmlFor={id} className="flex items-center justify-between gap-2 text-sm">
              <span className="font-mono tabular-nums">
                {currency} {denomination}
              </span>
              <Input
                id={id}
                className="w-24 text-right"
                inputMode="numeric"
                autoComplete="off"
                placeholder="0"
                value={value[denomination] ?? ''}
                onChange={(event) => onChange({ ...value, [denomination]: event.target.value })}
              />
            </label>
          );
        })}
      </div>
      <p className="text-sm">
        Total counted: <Money amount={countTotal(value)} currency={currency} className="font-semibold" />
      </p>
    </fieldset>
  );
}
