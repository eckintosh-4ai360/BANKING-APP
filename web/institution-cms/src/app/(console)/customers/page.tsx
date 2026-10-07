'use client';

import { bff, Permission, query, type CustomerSummary, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, FormField, Input, PageHeader, Select, StatusBadge, formatDate, humanize } from '@banking/ui';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus, Search } from 'lucide-react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState, type FormEvent } from 'react';
import { Can } from '@/lib/me';
import { useBranches } from '@/lib/queries';

const STATUSES = ['PENDING', 'ACTIVE', 'DORMANT', 'RESTRICTED', 'FROZEN', 'CLOSED'];
const KYC_STATUSES = ['NOT_STARTED', 'IN_PROGRESS', 'PENDING_REVIEW', 'VERIFIED', 'REJECTED', 'EXPIRED'];

export default function CustomersPage() {
  return (
    <Suspense>
      <CustomerSearch />
    </Suspense>
  );
}

function CustomerSearch() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const { branches, nameOf } = useBranches();

  // Filters live in the URL so they survive reloads; the search text does not, because names and phone numbers
  // must not end up in browser history or access logs.
  const [q, setQ] = useState('');
  const filters = {
    q,
    status: params.get('status') ?? '',
    kycStatus: params.get('kycStatus') ?? '',
    customerType: params.get('customerType') ?? '',
    branchId: params.get('branchId') ?? '',
  };
  const page = Number.parseInt(params.get('page') ?? '0', 10) || 0;
  const [text, setText] = useState('');

  function update(changes: Record<string, string | number>) {
    const next = new URLSearchParams(params.toString());
    for (const [key, value] of Object.entries(changes)) {
      if (value === '' || value === 0) {
        next.delete(key);
      } else {
        next.set(key, String(value));
      }
    }
    router.replace(`${pathname}${next.size ? `?${next.toString()}` : ''}`);
  }

  const tooShort = filters.q.length === 1;
  const result = useQuery({
    queryKey: ['customers', filters, page],
    queryFn: ({ signal }) => bff<Page<CustomerSummary>>(`/customers${query({ ...filters, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
    enabled: !tooShort,
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setQ(text.trim());
    update({ page: 0 });
  }

  const columns: ColumnDef<CustomerSummary, unknown>[] = [
    {
      header: 'Customer',
      cell: ({ row }) => (
        <Link href={`/customers/${row.original.id}`} className="grid" onClick={(event) => event.stopPropagation()}>
          <span className="font-medium text-foreground hover:underline">{row.original.displayName}</span>
          <span className="font-mono text-xs text-muted-foreground">{row.original.customerNumber}</span>
        </Link>
      ),
    },
    { header: 'Type', cell: ({ row }) => humanize(row.original.customerType) },
    { header: 'Phone', cell: ({ row }) => row.original.primaryPhone ?? '—' },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    { header: 'KYC', cell: ({ row }) => <StatusBadge status={row.original.kycStatus} /> },
    { header: 'Risk', cell: ({ row }) => <StatusBadge status={row.original.riskLevel} /> },
    { header: 'Branch', cell: ({ row }) => <span className="text-sm">{nameOf(row.original.homeBranchId)}</span> },
    { header: 'Created', cell: ({ row }) => formatDate(row.original.createdAt) },
  ];

  return (
    <>
      <PageHeader
        title="Customers"
        description="Search by customer number, name, phone or email."
        actions={
          <Can permission={Permission.customerCreate}>
            <Button onClick={() => router.push('/customers/new')}>
              <Plus aria-hidden="true" />
              New customer
            </Button>
          </Can>
        }
      />
      <div className="mb-4 grid gap-3 rounded-lg border bg-card p-4 lg:grid-cols-6">
        <form onSubmit={submit} className="flex items-end gap-2 lg:col-span-2">
          <FormField label="Search" className="flex-1" error={tooShort ? 'Enter at least 2 characters' : undefined}>
            {(control) => <Input {...control} type="search" value={text} onChange={(event) => setText(event.target.value)} placeholder="Name, number, phone or email" maxLength={100} />}
          </FormField>
          <Button type="submit" variant="outline" size="icon" aria-label="Search">
            <Search aria-hidden="true" />
          </Button>
        </form>
        <FormField label="Status">
          {(control) => (
            <Select {...control} value={filters.status} onChange={(event) => update({ status: event.target.value, page: 0 })}>
              <option value="">Any</option>
              {STATUSES.map((status) => (
                <option key={status} value={status}>
                  {humanize(status)}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="KYC status">
          {(control) => (
            <Select {...control} value={filters.kycStatus} onChange={(event) => update({ kycStatus: event.target.value, page: 0 })}>
              <option value="">Any</option>
              {KYC_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {humanize(status)}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="Type">
          {(control) => (
            <Select {...control} value={filters.customerType} onChange={(event) => update({ customerType: event.target.value, page: 0 })}>
              <option value="">Any</option>
              <option value="INDIVIDUAL">Individual</option>
              <option value="BUSINESS">Business</option>
            </Select>
          )}
        </FormField>
        <FormField label="Branch">
          {(control) => (
            <Select {...control} value={filters.branchId} onChange={(event) => update({ branchId: event.target.value, page: 0 })}>
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

      {result.isError ? <Alert tone="danger" className="mb-4">{errorMessage(result.error)}</Alert> : null}
      <DataTable
        caption="Customers"
        columns={columns}
        data={result.data?.items}
        loading={result.isLoading}
        pageInfo={result.data}
        onPageChange={(next) => update({ page: next })}
        onRowClick={(customer) => router.push(`/customers/${customer.id}`)}
        getRowId={(customer) => customer.id}
        emptyTitle="No customers found"
        emptyDescription={filters.q ? 'Check the spelling, or search by customer number or phone.' : undefined}
      />
    </>
  );
}
