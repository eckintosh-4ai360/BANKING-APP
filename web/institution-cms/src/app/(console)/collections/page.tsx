'use client';

import { bff, query, type ArrearsItem, type LoanPortfolio, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, DataTable, FormField, Money, PageHeader, Select, StatCard, formatDate, humanize } from '@banking/ui';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { LoanDialog } from '@/components/loan/loan-dialog';

export default function CollectionsPage() {
  const [minDays, setMinDays] = useState('1');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const portfolio = useQuery({ queryKey: ['collections', 'portfolio'], queryFn: ({ signal }) => bff<LoanPortfolio[]>('/loan-portfolio', { signal }) });
  const queue = useQuery({
    queryKey: ['collections', 'queue', minDays, page],
    queryFn: ({ signal }) => bff<Page<ArrearsItem>>(`/loans/arrears${query({ minDays, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<ArrearsItem, unknown>[] = [
    { header: 'Loan', cell: ({ row }) => <span className="font-mono text-sm">{row.original.loanNumber}</span> },
    { header: 'Customer', cell: ({ row }) => row.original.customerName ?? '—' },
    { header: 'Phone', cell: ({ row }) => row.original.customerPhone ?? '—' },
    { header: 'Days late', cell: ({ row }) => row.original.daysPastDue },
    { header: 'Band', cell: ({ row }) => humanize(row.original.delinquencyBand) },
    { header: 'Overdue', cell: ({ row }) => <Money amount={row.original.overdue} currency={row.original.currency} /> },
    {
      header: 'Last contact',
      cell: ({ row }) => {
        const last = row.original.lastActivity;
        if (!last) {
          return 'None yet';
        }
        return last.type === 'PROMISE' && last.promisedAmount ? (
          <span className="text-sm">
            Promised <Money amount={last.promisedAmount} currency={row.original.currency} /> by {formatDate(last.promisedDate)} ({humanize(last.promiseStatus)})
          </span>
        ) : (
          <span className="text-sm">
            {humanize(last.type)} · {formatDate(last.businessDate)}
          </span>
        );
      },
    },
  ];

  return (
    <>
      <PageHeader
        title="Collections"
        description="Loans in arrears, the longest overdue first, as end-of-day last classified them. Record calls, visits and promises to pay on each loan; end-of-day marks promises kept or broken on their date."
      />
      {portfolio.isError ? <Alert tone="danger" className="mb-4">{errorMessage(portfolio.error)}</Alert> : null}
      {portfolio.data?.map((summary) => (
        <section key={summary.currency} className="mb-6 grid gap-3">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            <StatCard label={`Active loans (${summary.currency})`} value={summary.activeLoans} />
            <StatCard label="Principal outstanding" value={<Money amount={summary.principalOutstanding} currency={summary.currency} />} />
            <StatCard label="Portfolio at risk > 30 days" value={`${summary.par30Percent}%`} hint={<Money amount={summary.portfolioAtRisk30} currency={summary.currency} />} />
            <StatCard label="Provision held" value={<Money amount={summary.provisionHeld} currency={summary.currency} />} />
            <StatCard label="Non-accrual loans" value={summary.nonAccrualLoans} />
          </div>
          <p className="text-sm text-muted-foreground">
            {summary.bands.map((band) => (
              <span key={band.code} className="mr-4">
                {band.name}: {band.loans} · <Money amount={band.principalOutstanding} currency={summary.currency} />
              </span>
            ))}
          </p>
        </section>
      ))}
      <FormField label="Late by at least" className="mb-4 w-48">
        {(control) => (
          <Select {...control} value={minDays} onChange={(event) => { setMinDays(event.target.value); setPage(0); }}>
            <option value="1">1 day</option>
            <option value="31">31 days</option>
            <option value="91">91 days</option>
            <option value="181">181 days</option>
          </Select>
        )}
      </FormField>
      {queue.isError ? <Alert tone="danger" className="mb-4">{errorMessage(queue.error)}</Alert> : null}
      <DataTable
        caption="Loans in arrears"
        columns={columns}
        data={queue.data?.items}
        loading={queue.isLoading}
        pageInfo={queue.data}
        onPageChange={setPage}
        onRowClick={(item) => setSelected(item.loanId)}
        getRowId={(item) => item.loanId}
        emptyTitle="No loans in arrears"
      />
      {selected ? <LoanDialog loanId={selected} initialTab="collections" onClose={() => setSelected(null)} /> : null}
    </>
  );
}
