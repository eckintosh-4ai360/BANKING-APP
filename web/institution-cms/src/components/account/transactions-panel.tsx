'use client';

import { bff, Permission, query, type Account, type Approval, type Page, type Transaction } from '@banking/api';
import { errorMessage } from '@banking/console';
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  DataTable,
  FormField,
  Modal,
  Money,
  StatusBadge,
  Textarea,
  formatDate,
  humanize,
} from '@banking/ui';
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { useCan } from '@/lib/me';

/** The account's transactions, newest first, with a way to ask for a reversal (which a second person approves). */
export function TransactionsPanel({ account }: { account: Account }) {
  const canReverse = useCan(Permission.transactionReverse);
  const [page, setPage] = useState(0);
  const [reversing, setReversing] = useState<Transaction | null>(null);
  const [requested, setRequested] = useState<Approval | null>(null);
  const transactions = useQuery({
    queryKey: ['account-transactions', account.id, page],
    queryFn: ({ signal }) => bff<Page<Transaction>>(`/accounts/${account.id}/transactions${query({ page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<Transaction, unknown>[] = [
    { header: 'Date', cell: ({ row }) => formatDate(row.original.businessDate) },
    {
      header: 'Transaction',
      cell: ({ row }) => (
        <span className="grid">
          <span>{humanize(row.original.transactionType)}</span>
          <span className="font-mono text-xs text-muted-foreground">{row.original.reference}</span>
        </span>
      ),
    },
    { header: 'Narration', cell: ({ row }) => row.original.narration ?? '—' },
    {
      header: 'In / out',
      cell: ({ row }) => {
        const outgoing = row.original.debitAccountId === account.id;
        return (
          <span className={outgoing ? 'text-destructive' : 'text-success'}>
            {outgoing ? '−' : '+'}
            <Money amount={row.original.amount} currency={row.original.currency} />
          </span>
        );
      },
    },
    {
      header: 'Charge',
      cell: ({ row }) =>
        /[1-9]/.test(row.original.feeAmount) && row.original.debitAccountId === account.id ? (
          <Money amount={row.original.feeAmount} currency={row.original.currency} />
        ) : (
          '—'
        ),
    },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    {
      header: '',
      id: 'actions',
      cell: ({ row }) =>
        canReverse && row.original.status === 'POSTED' ? (
          <Button size="sm" variant="ghost" onClick={() => setReversing(row.original)}>
            Reverse
          </Button>
        ) : null,
    },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Transactions</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4">
        {requested ? (
          <Alert tone="info" title="Reversal requested">
            {requested.summary}. It runs when another authorised person approves it.
          </Alert>
        ) : null}
        {transactions.isError ? <Alert tone="danger">{errorMessage(transactions.error)}</Alert> : null}
        <DataTable
          caption="Transactions"
          columns={columns}
          data={transactions.data?.items}
          loading={transactions.isLoading}
          pageInfo={transactions.data}
          onPageChange={setPage}
          getRowId={(transaction) => transaction.id}
          emptyTitle="No transactions in the last 90 days"
        />
      </CardContent>
      {reversing ? <ReversalDialog transaction={reversing} onClose={() => setReversing(null)} onDone={setRequested} /> : null}
    </Card>
  );
}

function ReversalDialog({ transaction, onClose, onDone }: { transaction: Transaction; onClose: () => void; onDone: (approval: Approval) => void }) {
  const [reason, setReason] = useState('');
  const request = useMutation({
    mutationFn: () => bff<Approval>(`/transactions/${transaction.id}/reversal`, { body: { reason: reason.trim() } }),
    onSuccess: (approval) => {
      onDone(approval);
      onClose();
    },
  });
  return (
    <Modal
      open
      onClose={onClose}
      busy={request.isPending}
      title="Request a reversal"
      description={
        <span>
          {humanize(transaction.transactionType)} {transaction.reference} of <Money amount={transaction.amount} currency={transaction.currency} />.
          The exact opposite entries, charges included, are posted once a second person approves.
        </span>
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={request.isPending}>
            Cancel
          </Button>
          <Button variant="destructive" loading={request.isPending} disabled={reason.trim() === ''} onClick={() => request.mutate()}>
            Request reversal
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {request.isError ? <Alert tone="danger">{errorMessage(request.error)}</Alert> : null}
        <FormField label="Reason" required>
          {(control) => <Textarea {...control} value={reason} maxLength={250} onChange={(event) => setReason(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
