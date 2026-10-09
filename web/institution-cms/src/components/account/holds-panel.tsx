'use client';

import { bff, Permission, type Account, type Hold } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  EmptyState,
  FormField,
  Input,
  Modal,
  Money,
  Select,
  Skeleton,
  StatusBadge,
  Textarea,
  formatDateTime,
  humanize,
} from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useCan } from '@/lib/me';
import { holdSchema, type HoldInput, type HoldValues } from '@/lib/schemas';

/** Liens, legal and fraud-review holds. They reserve funds without moving them, lowering the available balance. */
export function HoldsPanel({ account }: { account: Account }) {
  const queryClient = useQueryClient();
  const canManage = useCan(Permission.accountFreeze) && account.status !== 'CLOSED';
  const [placing, setPlacing] = useState(false);
  const [releasing, setReleasing] = useState<Hold | null>(null);
  const holds = useQuery({
    queryKey: ['account-holds', account.id],
    queryFn: ({ signal }) => bff<Hold[]>(`/accounts/${account.id}/holds`, { signal }),
  });

  function changed() {
    void queryClient.invalidateQueries({ queryKey: ['account-holds', account.id] });
    void queryClient.invalidateQueries({ queryKey: ['account', account.id] });
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-2">
        <CardTitle>Holds</CardTitle>
        {canManage ? (
          <Button size="sm" variant="outline" onClick={() => setPlacing(true)}>
            <Plus aria-hidden="true" />
            Place hold
          </Button>
        ) : null}
      </CardHeader>
      <CardContent>
        {holds.isPending ? <Skeleton className="h-16" /> : null}
        {holds.isError ? <Alert tone="danger">{errorMessage(holds.error)}</Alert> : null}
        {holds.data && holds.data.length === 0 ? <EmptyState title="No holds on this account" /> : null}
        {holds.data && holds.data.length > 0 ? (
          <ul className="divide-y" aria-label="Holds">
            {holds.data.map((hold) => (
              <li key={hold.id} className="flex flex-wrap items-start justify-between gap-2 py-3 text-sm">
                <div className="grid gap-0.5">
                  <p className="flex items-center gap-2 font-medium">
                    <Money amount={hold.amount} currency={hold.currency} /> {humanize(hold.holdType)} <StatusBadge status={hold.status} />
                  </p>
                  <p className="text-muted-foreground">{hold.reason}</p>
                  <p className="text-xs text-muted-foreground">
                    Placed {formatDateTime(hold.placedAt)}
                    {hold.expiresAt ? ` · expires ${formatDateTime(hold.expiresAt)}` : ''}
                    {hold.releaseReason ? ` · ${hold.releaseReason}` : ''}
                  </p>
                </div>
                {canManage && hold.status === 'ACTIVE' ? (
                  <Button size="sm" variant="ghost" onClick={() => setReleasing(hold)}>
                    Release
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        ) : null}
      </CardContent>
      {placing ? <PlaceHoldDialog account={account} onClose={() => setPlacing(false)} onDone={changed} /> : null}
      {releasing ? <ReleaseHoldDialog account={account} hold={releasing} onClose={() => setReleasing(null)} onDone={changed} /> : null}
    </Card>
  );
}

function PlaceHoldDialog({ account, onClose, onDone }: { account: Account; onClose: () => void; onDone: () => void }) {
  const form = useForm<HoldInput, unknown, HoldValues>({
    resolver: zodResolver(holdSchema),
    defaultValues: { amount: '', holdType: 'LIEN', reason: '', reference: '' },
  });
  const place = useMutation({
    mutationFn: (values: HoldValues) =>
      bff<Hold>(`/accounts/${account.id}/holds`, {
        body: { amount: values.amount, holdType: values.holdType, reason: values.reason, reference: values.reference },
      }),
    onSuccess: () => {
      onDone();
      onClose();
    },
    onError: (error) => applyFieldErrors(error, form.setError, ['amount', 'reason', 'reference']),
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={place.isPending}
      title="Place a hold"
      description={
        <span>
          Reserves funds on {account.accountNumber}. Available now: <Money amount={account.availableBalance} currency={account.currency} />
        </span>
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={place.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="hold-form" loading={place.isPending}>
            Place hold
          </Button>
        </>
      }
    >
      <form id="hold-form" className="grid gap-4" noValidate onSubmit={form.handleSubmit((values) => place.mutate(values))}>
        {place.isError ? <Alert tone="danger">{errorMessage(place.error)}</Alert> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label={`Amount (${account.currency})`} required error={form.formState.errors.amount?.message}>
            {(control) => <Input {...control} inputMode="decimal" autoComplete="off" {...form.register('amount')} />}
          </FormField>
          <FormField label="Type" required>
            {(control) => (
              <Select {...control} {...form.register('holdType')}>
                <option value="LIEN">Lien</option>
                <option value="LEGAL">Legal</option>
                <option value="FRAUD_REVIEW">Fraud review</option>
              </Select>
            )}
          </FormField>
        </div>
        <FormField label="Reason" required error={form.formState.errors.reason?.message}>
          {(control) => <Textarea {...control} maxLength={300} {...form.register('reason')} />}
        </FormField>
        <FormField label="Reference" hint="E.g. the court order or loan number" error={form.formState.errors.reference?.message}>
          {(control) => <Input {...control} maxLength={60} {...form.register('reference')} />}
        </FormField>
      </form>
    </Modal>
  );
}

function ReleaseHoldDialog({ account, hold, onClose, onDone }: { account: Account; hold: Hold; onClose: () => void; onDone: () => void }) {
  const [reason, setReason] = useState('');
  const release = useMutation({
    mutationFn: () => bff<Hold>(`/accounts/${account.id}/holds/${hold.id}/release`, { body: { reason: reason.trim(), version: hold.version } }),
    onSuccess: () => {
      onDone();
      onClose();
    },
  });
  return (
    <Modal
      open
      onClose={onClose}
      busy={release.isPending}
      title="Release hold"
      description={
        <span>
          Gives <Money amount={hold.amount} currency={hold.currency} /> back to the available balance.
        </span>
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={release.isPending}>
            Cancel
          </Button>
          <Button loading={release.isPending} disabled={reason.trim() === ''} onClick={() => release.mutate()}>
            Release
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {release.isError ? <Alert tone="danger">{errorMessage(release.error)}</Alert> : null}
        <FormField label="Reason" required>
          {(control) => <Textarea {...control} value={reason} maxLength={300} onChange={(event) => setReason(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
