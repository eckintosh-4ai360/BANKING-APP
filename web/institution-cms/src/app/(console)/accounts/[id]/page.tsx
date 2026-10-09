'use client';

import { bff, Permission, type Account, type MovementResult } from '@banking/api';
import { errorMessage } from '@banking/console';
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  DetailList,
  FormField,
  Modal,
  Money,
  PageHeader,
  Select,
  Skeleton,
  StatCard,
  StatusBadge,
  Textarea,
  formatDate,
  formatDateTime,
  humanize,
} from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { HoldsPanel } from '@/components/account/holds-panel';
import { MovementDialog, type MovementKind } from '@/components/account/movement-dialog';
import { StatementPanel } from '@/components/account/statement-panel';
import { TransactionsPanel } from '@/components/account/transactions-panel';
import { useCan } from '@/lib/me';
import { useBranches } from '@/lib/queries';

type LifecycleAction = 'status' | 'close';

export default function AccountPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const { nameOf } = useBranches();
  const canMoveMoney = useCan(Permission.transactionCreate);
  const canViewTransactions = useCan(Permission.transactionView);
  const canFreeze = useCan(Permission.accountFreeze);
  const canClose = useCan(Permission.accountClose);
  const [movement, setMovement] = useState<MovementKind | null>(null);
  const [lifecycle, setLifecycle] = useState<LifecycleAction | null>(null);
  const [outcome, setOutcome] = useState<MovementResult | null>(null);

  const account = useQuery({ queryKey: ['account', id], queryFn: ({ signal }) => bff<Account>(`/accounts/${id}`, { signal }) });

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['account', id] });
    void queryClient.invalidateQueries({ queryKey: ['account-transactions', id] });
    void queryClient.invalidateQueries({ queryKey: ['account-statement', id] });
    void queryClient.invalidateQueries({ queryKey: ['accounts'] });
  }

  if (account.isPending) {
    return <Skeleton className="h-64" />;
  }
  if (account.isError) {
    return <Alert tone="danger">{errorMessage(account.error)}</Alert>;
  }

  const data = account.data;
  const open = data.status !== 'CLOSED';
  const credits = ['PENDING', 'ACTIVE', 'RESTRICTED', 'DORMANT'].includes(data.status);
  const debits = data.status === 'ACTIVE';

  return (
    <>
      <PageHeader
        title={<span className="font-mono">{data.accountNumber}</span>}
        description={
          <span className="flex flex-wrap items-center gap-2">
            {data.title} · {data.productName} <StatusBadge status={data.status} />
          </span>
        }
        actions={
          <>
            {canMoveMoney && credits ? <Button onClick={() => setMovement('deposit')}>Deposit</Button> : null}
            {canMoveMoney && debits ? (
              <>
                <Button variant="outline" onClick={() => setMovement('withdrawal')}>
                  Withdraw
                </Button>
                <Button variant="outline" onClick={() => setMovement('transfer')}>
                  Transfer
                </Button>
              </>
            ) : null}
            {canFreeze && open ? (
              <Button variant="outline" onClick={() => setLifecycle('status')}>
                Change status
              </Button>
            ) : null}
            {canClose && open ? (
              <Button variant="destructive" onClick={() => setLifecycle('close')}>
                Close account
              </Button>
            ) : null}
          </>
        }
      />

      {outcome?.outcome === 'PENDING_APPROVAL' && outcome.approval ? (
        <Alert tone="info" className="mb-4" title="Waiting for approval">
          {outcome.approval.summary} is above the approval threshold. It posts when a supervisor approves it.
        </Alert>
      ) : null}
      {outcome?.outcome === 'POSTED' && outcome.transaction ? (
        <Alert tone="success" className="mb-4" title="Posted">
          {humanize(outcome.transaction.transactionType)} {outcome.transaction.reference} of{' '}
          <Money amount={outcome.transaction.amount} currency={outcome.transaction.currency} />
          {/[1-9]/.test(outcome.transaction.feeAmount) ? (
            <>
              {' '}
              (charge <Money amount={outcome.transaction.feeAmount} currency={outcome.transaction.currency} />)
            </>
          ) : null}
          .
        </Alert>
      ) : null}
      {data.statusReason && data.status !== 'ACTIVE' ? (
        <Alert tone="warning" className="mb-4" title={humanize(data.status)}>
          {data.statusReason}
        </Alert>
      ) : null}

      <div className="mb-6 grid gap-4 sm:grid-cols-3">
        <StatCard label="Balance" value={<Money amount={data.ledgerBalance} currency={data.currency} />} />
        <StatCard label="Available" value={<Money amount={data.availableBalance} currency={data.currency} />} hint="Balance less holds" />
        <StatCard label="On hold" value={<Money amount={data.holdAmount} currency={data.currency} />} />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Account</CardTitle>
          </CardHeader>
          <CardContent>
            <DetailList
              items={[
                { label: 'Product', value: `${data.productCode} · ${data.productName}` },
                { label: 'Currency', value: data.currency },
                { label: 'Ownership', value: humanize(data.ownershipType) },
                { label: 'Branch', value: nameOf(data.branchId) },
                { label: 'Opened', value: formatDate(data.openedOn) },
                { label: 'Activated', value: formatDateTime(data.activatedAt) },
                { label: 'Last activity', value: formatDateTime(data.lastActivityAt) },
                { label: 'Closed', value: formatDate(data.closedOn) },
              ]}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Holders</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="divide-y" aria-label="Holders">
              {data.holders.map((holder) => (
                <li key={holder.customerId} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <Link href={`/customers/${holder.customerId}`} className="grid hover:underline">
                    <span className="font-medium">{holder.displayName ?? 'Customer'}</span>
                    <span className="font-mono text-xs text-muted-foreground">{holder.customerNumber}</span>
                  </Link>
                  <span className="text-xs text-muted-foreground">{humanize(holder.role)}</span>
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>

      <div className="mt-6 grid gap-6">
        <HoldsPanel account={data} />
        {canViewTransactions ? <TransactionsPanel account={data} /> : null}
        {canViewTransactions ? <StatementPanel account={data} /> : null}
      </div>

      {movement ? (
        <MovementDialog
          kind={movement}
          account={data}
          onClose={() => setMovement(null)}
          onDone={(result) => {
            setOutcome(result);
            refresh();
          }}
        />
      ) : null}
      {lifecycle ? <LifecycleDialog action={lifecycle} account={data} onClose={() => setLifecycle(null)} onDone={refresh} /> : null}
    </>
  );
}

function LifecycleDialog({ action, account, onClose, onDone }: { action: LifecycleAction; account: Account; onClose: () => void; onDone: () => void }) {
  const [status, setStatus] = useState(account.status === 'ACTIVE' ? 'RESTRICTED' : 'ACTIVE');
  const [reason, setReason] = useState('');
  const save = useMutation({
    mutationFn: () =>
      action === 'close'
        ? bff<Account>(`/accounts/${account.id}/close`, { body: { reason: reason.trim(), version: account.version } })
        : bff<Account>(`/accounts/${account.id}/status`, { body: { status, reason: reason.trim(), version: account.version } }),
    onSuccess: () => {
      onDone();
      onClose();
    },
  });
  const closing = action === 'close';
  return (
    <Modal
      open
      onClose={onClose}
      busy={save.isPending}
      title={closing ? 'Close account' : 'Change account status'}
      description={
        closing
          ? 'Only an account with a zero balance and no active holds can be closed. Pay out the balance first.'
          : 'Restricted accounts accept money but pay nothing out; frozen accounts do neither.'
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button variant={closing ? 'destructive' : 'primary'} loading={save.isPending} disabled={reason.trim() === ''} onClick={() => save.mutate()}>
            {closing ? 'Close account' : 'Save status'}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {save.isError ? <Alert tone="danger">{errorMessage(save.error)}</Alert> : null}
        {closing ? null : (
          <FormField label="New status" required>
            {(control) => (
              <Select {...control} value={status} onChange={(event) => setStatus(event.target.value)}>
                <option value="ACTIVE">Active</option>
                <option value="RESTRICTED">Restricted (no debits)</option>
                <option value="FROZEN">Frozen (nothing moves)</option>
              </Select>
            )}
          </FormField>
        )}
        <FormField label="Reason" required>
          {(control) => <Textarea {...control} value={reason} maxLength={300} onChange={(event) => setReason(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
