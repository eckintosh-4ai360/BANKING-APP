'use client';

import { bff, Permission, type Product, type ProductVersion } from '@banking/api';
import { errorMessage } from '@banking/console';
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  DetailList,
  Modal,
  Money,
  PageHeader,
  Skeleton,
  StatusBadge,
  formatDateTime,
  humanize,
} from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { ProductFormDialog } from '@/components/product/product-form';
import { useCan } from '@/lib/me';

export default function ProductPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const canManage = useCan(Permission.productManage);
  const [editing, setEditing] = useState<ProductVersion | null>(null);
  const [publishing, setPublishing] = useState<ProductVersion | null>(null);
  const product = useQuery({ queryKey: ['product', id], queryFn: ({ signal }) => bff<Product>(`/products/${id}`, { signal }) });

  function updated(next: Product) {
    queryClient.setQueryData(['product', id], next);
    void queryClient.invalidateQueries({ queryKey: ['products'] });
  }

  const newDraft = useMutation({ mutationFn: () => bff<Product>(`/products/${id}/versions`, { method: 'POST' }), onSuccess: updated });
  const toggle = useMutation({
    mutationFn: (current: Product) =>
      bff<Product>(`/products/${id}`, {
        method: 'PUT',
        body: {
          name: current.name,
          description: current.description ?? undefined,
          status: current.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE',
          version: current.version,
        },
      }),
    onSuccess: updated,
  });

  if (product.isPending) {
    return <Skeleton className="h-64" />;
  }
  if (product.isError) {
    return <Alert tone="danger">{errorMessage(product.error)}</Alert>;
  }
  const data = product.data;
  const hasDraft = data.versions.some((version) => version.status === 'DRAFT');
  const actionError = newDraft.error ?? toggle.error;

  return (
    <>
      <PageHeader
        title={data.name}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span className="font-mono">{data.code}</span> · {humanize(data.productType)} <StatusBadge status={data.status} />
          </span>
        }
        actions={
          canManage ? (
            <>
              {!hasDraft ? (
                <Button variant="outline" loading={newDraft.isPending} onClick={() => newDraft.mutate()}>
                  New version
                </Button>
              ) : null}
              <Button variant="outline" loading={toggle.isPending} onClick={() => toggle.mutate(data)}>
                {data.status === 'ACTIVE' ? 'Stop offering' : 'Offer again'}
              </Button>
            </>
          ) : null
        }
      />
      {actionError ? <Alert tone="danger" className="mb-4">{errorMessage(actionError)}</Alert> : null}
      {data.description ? <p className="mb-4 text-sm text-muted-foreground">{data.description}</p> : null}

      <div className="grid gap-6">
        {data.versions.map((version) => (
          <Card key={version.id}>
            <CardHeader className="flex flex-row items-center justify-between gap-2">
              <CardTitle className="flex items-center gap-2">
                Version {version.versionNo} <StatusBadge status={version.status} />
                {data.currentVersion?.id === version.id ? <span className="text-xs font-normal text-muted-foreground">offered to new accounts</span> : null}
              </CardTitle>
              {canManage && version.status === 'DRAFT' ? (
                <span className="flex gap-2">
                  <Button size="sm" variant="outline" onClick={() => setEditing(version)}>
                    Edit terms
                  </Button>
                  <Button size="sm" onClick={() => setPublishing(version)}>
                    Publish
                  </Button>
                </span>
              ) : null}
            </CardHeader>
            <CardContent>
              <VersionTerms version={version} />
            </CardContent>
          </Card>
        ))}
      </div>

      {editing ? <ProductFormDialog product={data} draft={editing} onClose={() => setEditing(null)} onDone={updated} /> : null}
      {publishing ? <PublishDialog product={data} version={publishing} onClose={() => setPublishing(null)} onDone={updated} /> : null}
    </>
  );
}

function VersionTerms({ version }: { version: ProductVersion }) {
  const money = (amount: string | null) => (amount && /[1-9]/.test(amount) ? <Money amount={amount} currency={version.currency} /> : null);
  return (
    <div className="grid gap-4">
      <DetailList
        columns={3}
        items={[
          { label: 'Currency', value: version.currency },
          { label: 'Interest', value: `${version.interestRate}% a year · ${humanize(version.interestPostingFrequency)}` },
          { label: 'Required KYC tier', value: version.requiredKycTier ?? 'Any verified customer' },
          { label: 'Opening deposit', value: money(version.minOpeningBalance) },
          { label: 'Minimum balance', value: money(version.minOperatingBalance) },
          { label: 'Maximum balance', value: money(version.maxBalance) },
          { label: 'Largest withdrawal', value: money(version.maxWithdrawalAmount) },
          { label: 'Daily withdrawal limit', value: money(version.dailyWithdrawalLimit) },
          { label: 'Dormant after', value: `${version.dormancyDays} days without activity` },
          { label: 'Created', value: formatDateTime(version.createdAt) },
          { label: 'Published', value: formatDateTime(version.publishedAt) },
        ]}
      />
      <div>
        <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">Charges</p>
        {version.charges.length === 0 ? (
          <p className="text-sm text-muted-foreground">No charges</p>
        ) : (
          <ul className="grid gap-1 text-sm">
            {version.charges.map((charge) => (
              <li key={charge.event}>
                {charge.name} ({humanize(charge.event)}):{' '}
                {charge.calculation === 'FLAT' ? (
                  <Money amount={charge.flatAmount} currency={version.currency} />
                ) : (
                  <>
                    {charge.rate}% {charge.minAmount ? <>min <Money amount={charge.minAmount} currency={version.currency} /> </> : null}
                    {charge.maxAmount ? <>max <Money amount={charge.maxAmount} currency={version.currency} /></> : null}
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function PublishDialog({ product, version, onClose, onDone }: { product: Product; version: ProductVersion; onClose: () => void; onDone: (next: Product) => void }) {
  const publish = useMutation({
    mutationFn: () => bff<Product>(`/products/${product.id}/versions/${version.id}/publish`, { method: 'POST' }),
    onSuccess: (next) => {
      onDone(next);
      onClose();
    },
  });
  return (
    <Modal
      open
      onClose={onClose}
      busy={publish.isPending}
      title={`Publish version ${version.versionNo}`}
      description="New accounts get these terms from now on. Published terms can never be edited; accounts already open keep their own version."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={publish.isPending}>
            Cancel
          </Button>
          <Button loading={publish.isPending} onClick={() => publish.mutate()}>
            Publish
          </Button>
        </>
      }
    >
      {publish.isError ? <Alert tone="danger">{errorMessage(publish.error)}</Alert> : <VersionTerms version={version} />}
    </Modal>
  );
}
