'use client';

import { bff, query, type KycCaseSummary, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, DataTable, FormField, PageHeader, Select, StatusBadge, formatDateTime, humanize } from '@banking/ui';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useBranches } from '@/lib/queries';

const STATUSES = ['PENDING_REVIEW', 'OPEN', 'RETURNED', 'APPROVED', 'REJECTED', 'CANCELLED'];

export default function KycQueuePage() {
  const router = useRouter();
  const { branches, nameOf } = useBranches();
  const [status, setStatus] = useState('PENDING_REVIEW');
  const [branchId, setBranchId] = useState('');
  const [page, setPage] = useState(0);

  const cases = useQuery({
    queryKey: ['kyc-cases', status, branchId, page],
    queryFn: ({ signal }) => bff<Page<KycCaseSummary>>(`/kyc/cases${query({ status, branchId, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<KycCaseSummary, unknown>[] = [
    {
      header: 'Customer',
      cell: ({ row }) => (
        <span className="grid">
          <span className="font-medium">{row.original.customerName ?? '—'}</span>
          <span className="font-mono text-xs text-muted-foreground">{row.original.customerNumber}</span>
        </span>
      ),
    },
    { header: 'Case', cell: ({ row }) => humanize(row.original.caseType) },
    { header: 'Target tier', cell: ({ row }) => row.original.targetTierCode },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    { header: 'Branch', cell: ({ row }) => nameOf(row.original.branchId) },
    { header: 'Submitted', cell: ({ row }) => formatDateTime(row.original.submittedAt) },
    { header: 'Opened', cell: ({ row }) => formatDateTime(row.original.createdAt) },
  ];

  return (
    <>
      <PageHeader title="KYC reviews" description="Cases in your branch scope, oldest first. A case can't be decided by the officer who submitted it." />
      <div className="mb-4 grid gap-3 rounded-lg border bg-card p-4 sm:grid-cols-3">
        <FormField label="Status">
          {(control) => (
            <Select
              {...control}
              value={status}
              onChange={(event) => {
                setStatus(event.target.value);
                setPage(0);
              }}
            >
              {STATUSES.map((option) => (
                <option key={option} value={option}>
                  {humanize(option)}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="Branch">
          {(control) => (
            <Select
              {...control}
              value={branchId}
              onChange={(event) => {
                setBranchId(event.target.value);
                setPage(0);
              }}
            >
              <option value="">All in scope</option>
              {branches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name}
                </option>
              ))}
            </Select>
          )}
        </FormField>
      </div>
      {cases.isError ? <Alert tone="danger" className="mb-4">{errorMessage(cases.error)}</Alert> : null}
      <DataTable
        caption="KYC cases"
        columns={columns}
        data={cases.data?.items}
        loading={cases.isLoading}
        pageInfo={cases.data}
        onPageChange={setPage}
        onRowClick={(kycCase) => router.push(`/kyc/${kycCase.id}`)}
        getRowId={(kycCase) => kycCase.id}
        emptyTitle={`No ${humanize(status).toLowerCase()} cases`}
      />
    </>
  );
}
