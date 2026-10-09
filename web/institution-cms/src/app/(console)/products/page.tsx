'use client';

import { Permission, type Product } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, Money, PageHeader, StatusBadge, humanize } from '@banking/ui';
import { useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { ProductFormDialog } from '@/components/product/product-form';
import { Can } from '@/lib/me';
import { useProducts } from '@/lib/queries';

export default function ProductsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const products = useProducts();
  const [creating, setCreating] = useState(false);

  const columns: ColumnDef<Product, unknown>[] = [
    {
      header: 'Product',
      cell: ({ row }) => (
        <Link href={`/products/${row.original.id}`} className="grid" onClick={(event) => event.stopPropagation()}>
          <span className="font-medium text-foreground hover:underline">{row.original.name}</span>
          <span className="font-mono text-xs text-muted-foreground">{row.original.code}</span>
        </Link>
      ),
    },
    { header: 'Type', cell: ({ row }) => humanize(row.original.productType) },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    {
      header: 'Offered terms',
      cell: ({ row }) => {
        const current = row.original.currentVersion;
        return current ? (
          <span className="text-sm">
            v{current.versionNo} · {current.currency} · opening <Money amount={current.minOpeningBalance} currency={current.currency} /> ·{' '}
            {current.interestRate}%
          </span>
        ) : (
          <span className="text-sm text-muted-foreground">Not published yet</span>
        );
      },
    },
  ];

  return (
    <>
      <PageHeader
        title="Deposit products"
        description="Savings, current, susu and deposit products with versioned terms."
        actions={
          <Can permission={Permission.productManage}>
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden="true" />
              New product
            </Button>
          </Can>
        }
      />
      {products.isError ? <Alert tone="danger" className="mb-4">{errorMessage(products.error)}</Alert> : null}
      <DataTable
        caption="Deposit products"
        columns={columns}
        data={products.data}
        loading={products.isLoading}
        onRowClick={(product) => router.push(`/products/${product.id}`)}
        getRowId={(product) => product.id}
        emptyTitle="No products yet"
      />
      {creating ? (
        <ProductFormDialog
          onClose={() => setCreating(false)}
          onDone={(product) => {
            void queryClient.invalidateQueries({ queryKey: ['products'] });
            router.push(`/products/${product.id}`);
          }}
        />
      ) : null}
    </>
  );
}
