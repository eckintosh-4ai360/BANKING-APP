'use client';

import { bff, Permission, query, type Page, type TenantSummary } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, PageHeader, StatusBadge, humanize } from '@banking/ui';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useCan } from '@/lib/me';

const columns: ColumnDef<TenantSummary, unknown>[] = [
  {
    header: 'Institution',
    cell: ({ row }) => (
      <span className="grid">
        <span className="font-medium">{row.original.displayName}</span>
        <span className="text-xs text-muted-foreground">{row.original.legalName}</span>
      </span>
    ),
  },
  { header: 'Code', cell: ({ row }) => <code className="text-sm">{row.original.code}</code> },
  { header: 'Type', cell: ({ row }) => humanize(row.original.institutionType) },
  { header: 'Country', cell: ({ row }) => `${row.original.countryCode} · ${row.original.baseCurrency}` },
  { header: 'Licence', cell: ({ row }) => row.original.licenceNumber ?? '—' },
  { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
];

export default function TenantsPage() {
  const router = useRouter();
  const canView = useCan(Permission.platformTenantView);
  const canManage = useCan(Permission.platformTenantManage);
  const [page, setPage] = useState(0);
  const tenants = useQuery({
    queryKey: ['tenants', page],
    queryFn: ({ signal }) => bff<Page<TenantSummary>>(`/platform/tenants${query({ page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
    enabled: canView,
  });

  if (!canView) {
    return <Alert tone="info">You don't have access to institutions.</Alert>;
  }

  return (
    <>
      <PageHeader
        title="Institutions"
        description="Financial institutions hosted on the platform."
        actions={
          canManage ? (
            <Button onClick={() => router.push('/tenants/new')}>
              <Plus aria-hidden="true" />
              Onboard institution
            </Button>
          ) : null
        }
      />
      {tenants.isError ? <Alert tone="danger" className="mb-4">{errorMessage(tenants.error)}</Alert> : null}
      <DataTable
        caption="Institutions"
        columns={columns}
        data={tenants.data?.items}
        loading={tenants.isLoading}
        pageInfo={tenants.data}
        onPageChange={setPage}
        onRowClick={(tenant) => router.push(`/tenants/${tenant.id}`)}
        getRowId={(tenant) => tenant.id}
        emptyTitle="No institutions yet"
      />
    </>
  );
}
