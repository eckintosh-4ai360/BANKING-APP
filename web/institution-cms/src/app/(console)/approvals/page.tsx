'use client';

import { bff, Permission, query, type Approval, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, DataTable, FormField, Money, PageHeader, Select, StatusBadge, formatDateTime, humanize } from '@banking/ui';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { DecisionDialog } from '@/components/approval/decision-dialog';
import { ThresholdsCard } from '@/components/approval/thresholds-card';
import { useCan } from '@/lib/me';
import { useBranches } from '@/lib/queries';

const TYPES = ['TRANSACTION_REVERSAL', 'MANUAL_JOURNAL', 'CASH_WITHDRAWAL', 'TRANSFER'];

export default function ApprovalsPage() {
  const { nameOf } = useBranches();
  const canSeePolicies = useCan(Permission.settingsView, Permission.settingsManage, Permission.approvalView);
  const [status, setStatus] = useState('PENDING');
  const [type, setType] = useState('');
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<Approval | null>(null);
  const approvals = useQuery({
    queryKey: ['approvals', status, type, page],
    queryFn: ({ signal }) => bff<Page<Approval>>(`/approvals${query({ status, type, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<Approval, unknown>[] = [
    { header: 'Requested', cell: ({ row }) => formatDateTime(row.original.requestedAt) },
    { header: 'Type', cell: ({ row }) => humanize(row.original.requestType) },
    { header: 'Summary', cell: ({ row }) => <span className="text-sm">{row.original.summary}</span> },
    {
      header: 'Amount',
      cell: ({ row }) =>
        row.original.amount && row.original.currency ? <Money amount={row.original.amount} currency={row.original.currency} /> : '—',
    },
    { header: 'Branch', cell: ({ row }) => <span className="text-sm">{nameOf(row.original.branchId)}</span> },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <>
      <PageHeader title="Approvals" description="Actions waiting for a second person: reversals, manual journals and movements above the thresholds." />
      <div className="mb-4 grid gap-3 rounded-lg border bg-card p-4 sm:grid-cols-3">
        <FormField label="Status">
          {(control) => (
            <Select {...control} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
              <option value="PENDING">Pending</option>
              <option value="APPROVED">Approved</option>
              <option value="REJECTED">Rejected</option>
              <option value="CANCELLED">Cancelled</option>
            </Select>
          )}
        </FormField>
        <FormField label="Type">
          {(control) => (
            <Select {...control} value={type} onChange={(event) => { setType(event.target.value); setPage(0); }}>
              <option value="">Any</option>
              {TYPES.map((value) => (
                <option key={value} value={value}>
                  {humanize(value)}
                </option>
              ))}
            </Select>
          )}
        </FormField>
      </div>
      {approvals.isError ? <Alert tone="danger" className="mb-4">{errorMessage(approvals.error)}</Alert> : null}
      <DataTable
        caption="Approval requests"
        columns={columns}
        data={approvals.data?.items}
        loading={approvals.isLoading}
        pageInfo={approvals.data}
        onPageChange={setPage}
        onRowClick={setSelected}
        getRowId={(approval) => approval.id}
        emptyTitle={status === 'PENDING' ? 'Nothing is waiting for approval' : 'No requests'}
      />
      {canSeePolicies ? <ThresholdsCard /> : null}
      {selected ? <DecisionDialog approval={selected} onClose={() => setSelected(null)} /> : null}
    </>
  );
}
