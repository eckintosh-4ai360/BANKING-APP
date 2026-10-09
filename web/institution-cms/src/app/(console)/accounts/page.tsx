'use client';

import { bff, query, type AccountSummary, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, FormField, Input, Money, PageHeader, Select, StatusBadge, formatDate } from '@banking/ui';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Search } from 'lucide-react';
import Link from 'next/link';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useState, type FormEvent } from 'react';
import { useBranches, useProducts } from '@/lib/queries';
import { ACCOUNT_NUMBER } from '@/lib/schemas';

const STATUSES = ['PENDING', 'ACTIVE', 'RESTRICTED', 'FROZEN', 'DORMANT', 'CLOSED'];

export default function AccountsPage() {
  return (
    <Suspense>
      <AccountSearch />
    </Suspense>
  );
}

function AccountSearch() {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();
  const { branches, nameOf } = useBranches();
  const products = useProducts();
  const [accountNumber, setAccountNumber] = useState('');
  const [text, setText] = useState('');
  const filters = {
    accountNumber,
    status: params.get('status') ?? '',
    branchId: params.get('branchId') ?? '',
    productId: params.get('productId') ?? '',
  };
  const page = Number.parseInt(params.get('page') ?? '0', 10) || 0;
  const invalidNumber = text.trim() !== '' && !ACCOUNT_NUMBER.test(text.trim());

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

  const result = useQuery({
    queryKey: ['accounts', filters, page],
    queryFn: ({ signal }) => bff<Page<AccountSummary>>(`/accounts${query({ ...filters, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!invalidNumber) {
      setAccountNumber(text.trim());
      update({ page: 0 });
    }
  }

  const columns: ColumnDef<AccountSummary, unknown>[] = [
    {
      header: 'Account',
      cell: ({ row }) => (
        <Link href={`/accounts/${row.original.id}`} className="grid" onClick={(event) => event.stopPropagation()}>
          <span className="font-mono font-medium text-foreground hover:underline">{row.original.accountNumber}</span>
          <span className="text-xs text-muted-foreground">{row.original.title}</span>
        </Link>
      ),
    },
    { header: 'Product', cell: ({ row }) => row.original.productCode },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    {
      header: 'Balance',
      cell: ({ row }) => <Money amount={row.original.ledgerBalance} currency={row.original.currency} />,
    },
    {
      header: 'Available',
      cell: ({ row }) => <Money amount={row.original.availableBalance} currency={row.original.currency} />,
    },
    { header: 'Branch', cell: ({ row }) => <span className="text-sm">{nameOf(row.original.branchId)}</span> },
    { header: 'Opened', cell: ({ row }) => formatDate(row.original.openedOn) },
  ];

  return (
    <>
      <PageHeader title="Accounts" description="Deposit accounts in your branches. Open new accounts from the customer's record." />
      <div className="mb-4 grid gap-3 rounded-lg border bg-card p-4 lg:grid-cols-5">
        <form onSubmit={submit} className="flex items-end gap-2 lg:col-span-2">
          <FormField label="Account number" className="flex-1" error={invalidNumber ? 'Use the full 10 to 20 digit number' : undefined}>
            {(control) => (
              <Input {...control} type="search" inputMode="numeric" value={text} onChange={(event) => setText(event.target.value)} maxLength={20} />
            )}
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
                  {status.charAt(0) + status.slice(1).toLowerCase()}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="Product">
          {(control) => (
            <Select {...control} value={filters.productId} onChange={(event) => update({ productId: event.target.value, page: 0 })}>
              <option value="">Any</option>
              {(products.data ?? []).map((product) => (
                <option key={product.id} value={product.id}>
                  {product.code} · {product.name}
                </option>
              ))}
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
        caption="Accounts"
        columns={columns}
        data={result.data?.items}
        loading={result.isLoading}
        pageInfo={result.data}
        onPageChange={(next) => update({ page: next })}
        onRowClick={(account) => router.push(`/accounts/${account.id}`)}
        getRowId={(account) => account.id}
        emptyTitle="No accounts found"
      />
    </>
  );
}
