'use client';

import { bff, newIdempotencyKey, query, type Account, type AccountSummary, type MovementResult, type Page } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import { Alert, Button, FormField, Input, Modal, Money } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { movementSchema, type MovementInput, type MovementValues } from '@/lib/schemas';

export type MovementKind = 'deposit' | 'withdrawal' | 'transfer';

const META: Record<MovementKind, { title: string; path: string; confirm: string }> = {
  deposit: { title: 'Cash deposit', path: '/transactions/deposits', confirm: 'Post deposit' },
  withdrawal: { title: 'Cash withdrawal', path: '/transactions/withdrawals', confirm: 'Post withdrawal' },
  transfer: { title: 'Transfer to another account', path: '/transactions/transfers', confirm: 'Post transfer' },
};

/**
 * Deposit, withdrawal or transfer for one account. One idempotency key is created when the dialog opens and reused
 * for every retry from it, so a double click or a lost response can never move the money twice. Charges and limits
 * are applied by the server; the dialog only sends the amount as typed.
 */
export function MovementDialog({
  kind,
  account,
  onClose,
  onDone,
}: {
  kind: MovementKind;
  account: Account;
  onClose: () => void;
  onDone: (result: MovementResult) => void;
}) {
  const [idempotencyKey] = useState(newIdempotencyKey);
  const meta = META[kind];
  const form = useForm<MovementInput, unknown, MovementValues>({
    resolver: zodResolver(movementSchema(kind === 'transfer')),
    defaultValues: { amount: '', narration: '', externalReference: '', toAccountNumber: '' },
  });

  const post = useMutation({
    mutationFn: async (values: MovementValues) => {
      const common = {
        amount: values.amount,
        narration: values.narration,
        externalReference: values.externalReference,
      };
      if (kind === 'transfer') {
        const target = await bff<Page<AccountSummary>>(`/accounts${query({ accountNumber: values.toAccountNumber, size: 1 })}`);
        const toAccount = target.items[0];
        if (!toAccount) {
          throw new Error('NO_TARGET');
        }
        return bff<MovementResult>(meta.path, {
          body: { fromAccountId: account.id, toAccountId: toAccount.id, ...common },
          idempotencyKey,
        });
      }
      return bff<MovementResult>(meta.path, { body: { accountId: account.id, ...common }, idempotencyKey });
    },
    onSuccess: (result) => {
      onDone(result);
      onClose();
    },
    onError: (error) => {
      if (error instanceof Error && error.message === 'NO_TARGET') {
        form.setError('toAccountNumber', { type: 'server', message: 'No account with this number in your branches' });
        return;
      }
      applyFieldErrors(error, form.setError, ['amount', 'narration', 'externalReference']);
    },
  });

  const otherError = post.error instanceof Error && post.error.message === 'NO_TARGET' ? null : post.error;

  return (
    <Modal
      open
      onClose={onClose}
      busy={post.isPending}
      title={meta.title}
      description={
        <span>
          {account.accountNumber} · {account.title} · available <Money amount={account.availableBalance} currency={account.currency} />
        </span>
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={post.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="movement-form" loading={post.isPending}>
            {meta.confirm}
          </Button>
        </>
      }
    >
      <form id="movement-form" className="grid gap-4" noValidate onSubmit={form.handleSubmit((values) => post.mutate(values))}>
        {otherError ? <Alert tone="danger">{errorMessage(otherError)}</Alert> : null}
        {kind === 'transfer' ? (
          <FormField label="To account number" required error={form.formState.errors.toAccountNumber?.message}>
            {(control) => <Input {...control} inputMode="numeric" autoComplete="off" {...form.register('toAccountNumber')} />}
          </FormField>
        ) : null}
        <FormField
          label={`Amount (${account.currency})`}
          required
          error={form.formState.errors.amount?.message}
          hint="Charges and limits of the account's product are applied by the system."
        >
          {(control) => <Input {...control} inputMode="decimal" autoComplete="off" placeholder="0.00" {...form.register('amount')} />}
        </FormField>
        <FormField label="Narration" error={form.formState.errors.narration?.message}>
          {(control) => <Input {...control} maxLength={200} {...form.register('narration')} />}
        </FormField>
        <FormField label="External reference" hint="E.g. the deposit slip number" error={form.formState.errors.externalReference?.message}>
          {(control) => <Input {...control} maxLength={60} {...form.register('externalReference')} />}
        </FormField>
      </form>
    </Modal>
  );
}
