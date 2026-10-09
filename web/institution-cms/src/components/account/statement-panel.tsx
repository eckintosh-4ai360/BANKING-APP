'use client';

import { bff, query, type Account, type AccountStatement } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, Card, CardContent, CardHeader, CardTitle, FormField, Input, Money, formatDate } from '@banking/ui';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { useState } from 'react';
import { downloadThroughBff } from '@/lib/download';

/**
 * Statement from the ledger for a period (last 30 days by default), with PDF and CSV downloads. Downloads are
 * audited by the server.
 */
export function StatementPanel({ account }: { account: Account }) {
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [period, setPeriod] = useState<{ from: string; to: string }>({ from: '', to: '' });
  const statement = useQuery({
    queryKey: ['account-statement', account.id, period],
    queryFn: ({ signal }) => bff<AccountStatement>(`/accounts/${account.id}/statement${query(period)}`, { signal }),
  });
  const download = useMutation({
    mutationFn: (format: 'PDF' | 'CSV') =>
      downloadThroughBff(
        `/accounts/${account.id}/statement/download${query({ ...period, format })}`,
        `statement-${account.accountNumber}.${format.toLowerCase()}`,
      ),
  });

  const data = statement.data;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Statement</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4">
        <form
          className="flex flex-wrap items-end gap-3"
          onSubmit={(event) => {
            event.preventDefault();
            setPeriod({ from, to });
          }}
        >
          <FormField label="From">{(control) => <Input {...control} type="date" value={from} onChange={(event) => setFrom(event.target.value)} />}</FormField>
          <FormField label="To">{(control) => <Input {...control} type="date" value={to} onChange={(event) => setTo(event.target.value)} />}</FormField>
          <Button type="submit" variant="outline">
            Show
          </Button>
          <Button type="button" variant="outline" loading={download.isPending && download.variables === 'PDF'} onClick={() => download.mutate('PDF')}>
            <Download aria-hidden="true" />
            PDF
          </Button>
          <Button type="button" variant="outline" loading={download.isPending && download.variables === 'CSV'} onClick={() => download.mutate('CSV')}>
            <Download aria-hidden="true" />
            CSV
          </Button>
        </form>
        {statement.isError ? <Alert tone="danger">{errorMessage(statement.error)}</Alert> : null}
        {download.isError ? <Alert tone="danger">{errorMessage(download.error)}</Alert> : null}
        {data ? (
          <div className="overflow-x-auto">
            <table className="w-full text-sm" aria-label="Statement">
              <thead className="text-left text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="py-2 pr-3">Date</th>
                  <th className="py-2 pr-3">Reference</th>
                  <th className="py-2 pr-3">Description</th>
                  <th className="py-2 pr-3 text-right">Debit</th>
                  <th className="py-2 pr-3 text-right">Credit</th>
                  <th className="py-2 text-right">Balance</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                <tr>
                  <td className="py-2 pr-3">{formatDate(data.from)}</td>
                  <td />
                  <td className="py-2 pr-3 font-medium">Opening balance</td>
                  <td />
                  <td />
                  <td className="py-2 text-right">
                    <Money amount={data.openingBalance} currency={data.currency} />
                  </td>
                </tr>
                {data.lines.map((line, index) => (
                  <tr key={`${line.reference ?? 'line'}-${index}`}>
                    <td className="py-2 pr-3">{formatDate(line.date)}</td>
                    <td className="py-2 pr-3 font-mono text-xs">{line.reference}</td>
                    <td className="py-2 pr-3">{line.description}</td>
                    <td className="py-2 pr-3 text-right">{line.debit ? <Money amount={line.debit} currency={data.currency} /> : null}</td>
                    <td className="py-2 pr-3 text-right">{line.credit ? <Money amount={line.credit} currency={data.currency} /> : null}</td>
                    <td className="py-2 text-right">
                      <Money amount={line.balance} currency={data.currency} />
                    </td>
                  </tr>
                ))}
                <tr className="font-medium">
                  <td className="py-2 pr-3">{formatDate(data.to)}</td>
                  <td />
                  <td className="py-2 pr-3">Closing balance</td>
                  <td className="py-2 pr-3 text-right">
                    <Money amount={data.totalDebits} currency={data.currency} />
                  </td>
                  <td className="py-2 pr-3 text-right">
                    <Money amount={data.totalCredits} currency={data.currency} />
                  </td>
                  <td className="py-2 text-right">
                    <Money amount={data.closingBalance} currency={data.currency} />
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
