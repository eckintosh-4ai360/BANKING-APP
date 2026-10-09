'use client';

import { ApiError, bff, Permission, query, type Account, type AccountSummary, type CustomerDetail, type CustomerSummary, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, EmptyState, FormField, Input, Modal, Money, Select, Skeleton, StatusBadge, formatDate, humanize } from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useCan } from '@/lib/me';
import { useProducts } from '@/lib/queries';
import { Section } from './shared';

/** Accounts the customer holds, and opening a new one. */
export function AccountsPanel({ customer }: { customer: CustomerDetail }) {
  const canOpen = useCan(Permission.accountCreate) && customer.status === 'ACTIVE';
  const [opening, setOpening] = useState(false);
  const accounts = useQuery({
    queryKey: ['customer-accounts', customer.id],
    queryFn: ({ signal }) => bff<AccountSummary[]>(`/customers/${customer.id}/accounts`, { signal }),
  });

  return (
    <Section
      title="Accounts"
      action={
        canOpen ? (
          <Button size="sm" onClick={() => setOpening(true)}>
            <Plus aria-hidden="true" />
            Open account
          </Button>
        ) : null
      }
    >
      {customer.status !== 'ACTIVE' ? (
        <Alert tone="info" className="mb-3">
          Accounts can be opened once the customer is active (KYC approved).
        </Alert>
      ) : null}
      {accounts.isPending ? <Skeleton className="h-16" /> : null}
      {accounts.isError ? <Alert tone="danger">{errorMessage(accounts.error)}</Alert> : null}
      {accounts.data && accounts.data.length === 0 ? <EmptyState title="No accounts yet" /> : null}
      {accounts.data && accounts.data.length > 0 ? (
        <ul className="divide-y" aria-label="Accounts">
          {accounts.data.map((account) => (
            <li key={account.id} className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm">
              <Link href={`/accounts/${account.id}`} className="grid hover:underline">
                <span className="font-mono font-medium">{account.accountNumber}</span>
                <span className="text-xs text-muted-foreground">
                  {account.productCode} · opened {formatDate(account.openedOn)}
                </span>
              </Link>
              <span className="flex items-center gap-3">
                <Money amount={account.ledgerBalance} currency={account.currency} />
                <StatusBadge status={account.status} />
              </span>
            </li>
          ))}
        </ul>
      ) : null}
      {opening ? <OpenAccountDialog customer={customer} onClose={() => setOpening(false)} /> : null}
    </Section>
  );
}

function OpenAccountDialog({ customer, onClose }: { customer: CustomerDetail; onClose: () => void }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const products = useProducts();
  const business = customer.customerType === 'BUSINESS';
  const offered = (products.data ?? []).filter((product) => product.status === 'ACTIVE' && product.currentVersion);
  const [productId, setProductId] = useState('');
  const [ownership, setOwnership] = useState(business ? 'BUSINESS' : 'SINGLE');
  const [title, setTitle] = useState('');
  const [others, setOthers] = useState('');
  const joint = ownership === 'JOINT_ANY' || ownership === 'JOINT_ALL';
  const selected = offered.find((product) => product.id === productId);

  const open = useMutation({
    mutationFn: async () => {
      const numbers = others
        .split(/[\s,;]+/)
        .map((value) => value.trim())
        .filter(Boolean);
      const otherHolders = [];
      for (const number of numbers) {
        const found = await bff<Page<CustomerSummary>>(`/customers${query({ q: number, size: 5 })}`);
        const holder = found.items.find((candidate) => candidate.customerNumber === number);
        if (!holder) {
          throw new Error(`No customer with number ${number} in your branches.`);
        }
        otherHolders.push({ customerId: holder.id, role: joint ? 'JOINT' : 'SIGNATORY' });
      }
      return bff<Account>('/accounts', {
        body: { customerId: customer.id, productId, ownershipType: ownership, title: title.trim() || undefined, otherHolders },
      });
    },
    onSuccess: (account) => {
      void queryClient.invalidateQueries({ queryKey: ['customer-accounts', customer.id] });
      router.push(`/accounts/${account.id}`);
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={open.isPending}
      title="Open account"
      description={`For ${customer.displayName}. The account gets the product's published terms and keeps them.`}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={open.isPending}>
            Cancel
          </Button>
          <Button loading={open.isPending} disabled={!productId || (joint && others.trim() === '')} onClick={() => open.mutate()}>
            Open account
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {open.isError ? <Alert tone="danger">{open.error instanceof ApiError ? errorMessage(open.error) : open.error.message}</Alert> : null}
        <FormField label="Product" required>
          {(control) => (
            <Select {...control} value={productId} onChange={(event) => setProductId(event.target.value)}>
              <option value="">Choose a product</option>
              {offered.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.name} ({product.code}, {product.currentVersion?.currency})
                </option>
              ))}
            </Select>
          )}
        </FormField>
        {selected?.currentVersion && /[1-9]/.test(selected.currentVersion.minOpeningBalance) ? (
          <Alert tone="info">
            The account stays pending until an opening deposit of{' '}
            <Money amount={selected.currentVersion.minOpeningBalance} currency={selected.currentVersion.currency} /> is paid in.
          </Alert>
        ) : null}
        <FormField label="Ownership" required>
          {(control) => (
            <Select {...control} value={ownership} onChange={(event) => setOwnership(event.target.value)}>
              {(business ? ['BUSINESS'] : ['SINGLE', 'JOINT_ANY', 'JOINT_ALL']).map((value) => (
                <option key={value} value={value}>
                  {value === 'JOINT_ANY' ? 'Joint (any holder may act)' : value === 'JOINT_ALL' ? 'Joint (all holders act together)' : humanize(value)}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        {joint || business ? (
          <FormField
            label={joint ? 'Joint holders' : 'Signatories'}
            required={joint}
            hint="Customer numbers, separated by commas. Every holder must be an active customer."
          >
            {(control) => <Input {...control} value={others} onChange={(event) => setOthers(event.target.value)} />}
          </FormField>
        ) : null}
        <FormField label="Account title" hint="Defaults to the customer's name">
          {(control) => <Input {...control} value={title} maxLength={150} onChange={(event) => setTitle(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
