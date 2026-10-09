'use client';

import type { LoanInstallment, SchedulePreview, ScheduleLine } from '@banking/api';
import { DataTable, DetailList, Money, StatusBadge, formatDate } from '@banking/ui';
import type { ColumnDef } from '@tanstack/react-table';

/** A schedule the server computed for terms not yet disbursed, with its headline figures. */
export function PreviewSummary({ preview, currency }: { preview: SchedulePreview; currency: string }) {
  const columns: ColumnDef<ScheduleLine, unknown>[] = [
    { header: '#', cell: ({ row }) => row.original.number },
    { header: 'Due', cell: ({ row }) => formatDate(row.original.dueDate) },
    { header: 'Principal', cell: ({ row }) => <Money amount={row.original.principal} currency={currency} /> },
    { header: 'Interest', cell: ({ row }) => <Money amount={row.original.interest} currency={currency} /> },
    { header: 'Installment', cell: ({ row }) => <Money amount={row.original.total} currency={currency} /> },
    { header: 'Balance after', cell: ({ row }) => <Money amount={row.original.outstandingAfter} currency={currency} /> },
  ];
  return (
    <div className="grid gap-3">
      <DetailList
        columns={3}
        items={[
          { label: 'Processing fee', value: <Money amount={preview.processingFee} currency={currency} /> },
          { label: 'Paid into the account', value: <Money amount={preview.netDisbursed} currency={currency} /> },
          { label: 'Total interest', value: <Money amount={preview.totalInterest} currency={currency} /> },
          { label: 'Total to repay', value: <Money amount={preview.totalRepayable} currency={currency} /> },
          { label: 'Installment', value: preview.installmentAmount ? <Money amount={preview.installmentAmount} currency={currency} /> : 'Varies' },
          { label: 'Matures', value: formatDate(preview.maturityDate) },
        ]}
      />
      <div className="max-h-72 overflow-y-auto">
        <DataTable caption="Repayment schedule" columns={columns} data={preview.lines} getRowId={(line) => String(line.number)} />
      </div>
    </div>
  );
}

/** A disbursed loan's schedule and what was settled of each installment. */
export function InstallmentTable({ installments, currency }: { installments: LoanInstallment[]; currency: string }) {
  const columns: ColumnDef<LoanInstallment, unknown>[] = [
    { header: '#', cell: ({ row }) => row.original.number },
    { header: 'Due', cell: ({ row }) => formatDate(row.original.dueDate) },
    { header: 'Principal', cell: ({ row }) => <Money amount={row.original.principalDue} currency={currency} /> },
    { header: 'Interest', cell: ({ row }) => <Money amount={row.original.interestDue} currency={currency} /> },
    { header: 'Penalty', cell: ({ row }) => <Money amount={row.original.penaltyDue} currency={currency} /> },
    { header: 'Waived', cell: ({ row }) => <Money amount={row.original.interestWaived} currency={currency} /> },
    { header: 'Still owed', cell: ({ row }) => <Money amount={row.original.outstanding} currency={currency} /> },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];
  return <DataTable caption="Installments" columns={columns} data={installments} getRowId={(installment) => String(installment.number)} />;
}
