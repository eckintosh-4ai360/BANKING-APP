'use client';

import { bff, query, type Loan, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, DataTable, FormField, Money, PageHeader, Select, StatusBadge, formatDate, humanize } from '@banking/ui';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { LoanDialog } from '@/components/loan/loan-dialog';

export default function LoansPage() {
  const [status, setStatus] = useState('ACTIVE');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const loans = useQuery({
    queryKey: ['loans', 'list', status, page],
    queryFn: ({ signal }) => bff<Page<Loan>>(`/loans${query({ status, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<Loan, unknown>[] = [
    { header: 'Loan', cell: ({ row }) => <span className="font-mono text-sm">{row.original.loanNumber}</span> },
    { header: 'Customer', cell: ({ row }) => row.original.customerName ?? '—' },
    { header: 'Disbursed', cell: ({ row }) => <Money amount={row.original.principal} currency={row.original.currency} /> },
    { header: 'Principal owed', cell: ({ row }) => <Money amount={row.original.principalOutstanding} currency={row.original.currency} /> },
    {
      header: 'Arrears',
      cell: ({ row }) =>
        row.original.daysPastDue > 0 ? (
          <span className="text-destructive">
            <Money amount={row.original.arrears} currency={row.original.currency} /> · {row.original.daysPastDue} days
          </span>
        ) : '—',
    },
    { header: 'Band', cell: ({ row }) => humanize(row.original.delinquencyBand) },
    { header: 'Next due', cell: ({ row }) => formatDate(row.original.nextDueDate) },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <>
      <PageHeader
        title="Loans"
        description="Balances come from each loan's ledger accounts. Interest is recognised as the schedule earns it; end-of-day adds penalties, classifies arrears and sets the provision."
      />
      <FormField label="Status" className="mb-4 w-48">
        {(control) => (
          <Select {...control} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="ACTIVE">Active</option>
            <option value="CLOSED">Repaid</option>
            <option value="WRITTEN_OFF">Written off</option>
            <option value="">Any</option>
          </Select>
        )}
      </FormField>
      {loans.isError ? <Alert tone="danger" className="mb-4">{errorMessage(loans.error)}</Alert> : null}
      <DataTable
        caption="Loans"
        columns={columns}
        data={loans.data?.items}
        loading={loans.isLoading}
        pageInfo={loans.data}
        onPageChange={setPage}
        onRowClick={(loan) => setSelected(loan.id)}
        getRowId={(loan) => loan.id}
        emptyTitle="No loans"
      />
      {selected ? <LoanDialog loanId={selected} onClose={() => setSelected(null)} /> : null}
    </>
  );
}
