'use client';

import { bff, Permission, query, type LoanApplication, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, FormField, Money, PageHeader, Select, StatusBadge, formatDate } from '@banking/ui';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { ApplicationDialog } from '@/components/loan/application-dialog';
import { NewApplicationDialog } from '@/components/loan/new-application-dialog';
import { useCan } from '@/lib/me';

const STATUSES = ['DRAFT', 'SUBMITTED', 'ASSESSED', 'RECOMMENDED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'DISBURSED'];

export default function LoanApplicationsPage() {
  const canCreate = useCan(Permission.loanCreate);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const applications = useQuery({
    queryKey: ['loan-applications', 'list', status, page],
    queryFn: ({ signal }) => bff<Page<LoanApplication>>(`/loan-applications${query({ status, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<LoanApplication, unknown>[] = [
    { header: 'Application', cell: ({ row }) => <span className="font-mono text-sm">{row.original.applicationNumber}</span> },
    { header: 'Customer', cell: ({ row }) => row.original.customerName ?? '—' },
    { header: 'Product', cell: ({ row }) => row.original.productName ?? row.original.productCode },
    { header: 'Amount', cell: ({ row }) => <Money amount={row.original.approvedAmount ?? row.original.requestedAmount} currency={row.original.currency} /> },
    { header: 'Installments', cell: ({ row }) => row.original.approvedInstallments ?? row.original.requestedInstallments },
    { header: 'Loan officer', cell: ({ row }) => row.original.loanOfficerName ?? '—' },
    { header: 'Created', cell: ({ row }) => formatDate(row.original.createdAt) },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <>
      <PageHeader
        title="Loan applications"
        description="From application to disbursement: assessment, recommendation and approval are taken by different people, and guarantors and collateral count once someone other than the loan officer verifies them."
        actions={
          canCreate ? (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden="true" />
              New application
            </Button>
          ) : null
        }
      />
      <FormField label="Status" className="mb-4 w-48">
        {(control) => (
          <Select {...control} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="">Any</option>
            {STATUSES.map((code) => (
              <option key={code} value={code}>
                {code.charAt(0) + code.slice(1).toLowerCase()}
              </option>
            ))}
          </Select>
        )}
      </FormField>
      {applications.isError ? <Alert tone="danger" className="mb-4">{errorMessage(applications.error)}</Alert> : null}
      <DataTable
        caption="Loan applications"
        columns={columns}
        data={applications.data?.items}
        loading={applications.isLoading}
        pageInfo={applications.data}
        onPageChange={setPage}
        onRowClick={(application) => setSelected(application.id)}
        getRowId={(application) => application.id}
        emptyTitle="No applications"
      />
      {creating ? (
        <NewApplicationDialog
          onClose={() => setCreating(false)}
          onCreated={(detail) => {
            setCreating(false);
            setSelected(detail.application.id);
          }}
        />
      ) : null}
      {selected ? <ApplicationDialog applicationId={selected} onClose={() => setSelected(null)} /> : null}
    </>
  );
}
