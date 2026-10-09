'use client';

import { bff, query, type AccountSummary, type CustomerSummary, type LoanApplicationDetail, type LoanProduct, type Page, type SchedulePreview } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, FormField, Input, Modal, Select, Textarea } from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { AMOUNT } from '@/lib/schemas';
import { PreviewSummary } from './schedule-tables';

const DISBURSABLE = ['SAVINGS', 'CURRENT'];

/**
 * Starts an application on a product's published terms, with the signed-in officer as loan officer. The schedule
 * preview comes from the server; the browser never computes amounts.
 */
export function NewApplicationDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (detail: LoanApplicationDetail) => void }) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [productId, setProductId] = useState('');
  const [accountId, setAccountId] = useState('');
  const [amount, setAmount] = useState('');
  const [installments, setInstallments] = useState('');
  const [purpose, setPurpose] = useState('');
  const [monthlyIncome, setMonthlyIncome] = useState('');
  const [monthlyExpenses, setMonthlyExpenses] = useState('');
  const [existingDebt, setExistingDebt] = useState('');
  const customers = useQuery({
    queryKey: ['customers', 'search', search],
    queryFn: ({ signal }) => bff<Page<CustomerSummary>>(`/customers${query({ q: search, size: 20 })}`, { signal }),
    enabled: search.trim().length >= 2,
  });
  const products = useQuery({
    queryKey: ['loan-products'],
    queryFn: ({ signal }) => bff<LoanProduct[]>('/loan-products', { signal }),
    select: (all) => all.filter((product) => product.status === 'ACTIVE' && product.currentVersion),
  });
  const product = products.data?.find((candidate) => candidate.id === productId);
  const terms = product?.currentVersion ?? null;
  const accounts = useQuery({
    queryKey: ['customers', customerId, 'accounts'],
    queryFn: ({ signal }) => bff<AccountSummary[]>(`/customers/${customerId}/accounts`, { signal }),
    enabled: !!customerId,
    select: (all) => all.filter((account) => DISBURSABLE.includes(account.productType) && account.status === 'ACTIVE'),
  });
  const count = Number(installments);
  const optionalAmountsValid = [monthlyIncome, monthlyExpenses, existingDebt].every((value) => value === '' || AMOUNT.test(value));
  const termsValid = !!terms && AMOUNT.test(amount) && /[1-9]/.test(amount) && Number.isInteger(count) && count >= 1 && count <= 520;
  const valid = !!customerId && !!accountId && termsValid && purpose.trim().length > 0 && optionalAmountsValid;
  const preview = useQuery({
    queryKey: ['loan-products', productId, 'preview', amount, count],
    queryFn: ({ signal }) => bff<SchedulePreview>(`/loan-products/${productId}/schedule-preview${query({ amount, installments: count })}`, { signal }),
    enabled: false,
    retry: false,
  });
  const create = useMutation({
    mutationFn: () =>
      bff<LoanApplicationDetail>('/loan-applications', {
        body: {
          customerId,
          productId,
          requestedAmount: amount,
          requestedInstallments: count,
          purpose: purpose.trim(),
          disbursementAccountId: accountId,
          ...(monthlyIncome ? { monthlyIncome } : {}),
          ...(monthlyExpenses ? { monthlyExpenses } : {}),
          ...(existingDebt ? { existingDebt } : {}),
        },
      }),
    onSuccess: (detail) => {
      void queryClient.invalidateQueries({ queryKey: ['loan-applications'] });
      onCreated(detail);
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={create.isPending}
      className="max-w-3xl"
      title="New loan application"
      description="The application is made on the product's published terms; you are its loan officer, so someone else recommends and approves it."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={create.isPending}>
            Cancel
          </Button>
          <Button variant="outline" onClick={() => void preview.refetch()} disabled={!termsValid} loading={preview.isFetching}>
            Preview schedule
          </Button>
          <Button onClick={() => create.mutate()} disabled={!valid} loading={create.isPending}>
            Create application
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {create.isError ? <Alert tone="danger">{errorMessage(create.error)}</Alert> : null}
        {preview.isError ? <Alert tone="danger">{errorMessage(preview.error)}</Alert> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Find customer">
            {(control) => <Input {...control} value={search} onChange={(event) => setSearch(event.target.value)} />}
          </FormField>
          <FormField label="Customer" required>
            {(control) => (
              <Select {...control} value={customerId} onChange={(event) => { setCustomerId(event.target.value); setAccountId(''); }}>
                <option value="">Choose</option>
                {customers.data?.items.map((customer) => (
                  <option key={customer.id} value={customer.id}>
                    {customer.displayName} ({customer.customerNumber})
                  </option>
                ))}
              </Select>
            )}
          </FormField>
          <FormField label="Loan product" required>
            {(control) => (
              <Select {...control} value={productId} onChange={(event) => setProductId(event.target.value)}>
                <option value="">Choose</option>
                {products.data?.map((candidate) => (
                  <option key={candidate.id} value={candidate.id}>
                    {candidate.name} ({candidate.code})
                  </option>
                ))}
              </Select>
            )}
          </FormField>
          <FormField label="Pay into account" required hint={customerId && accounts.data?.length === 0 ? 'The customer has no active savings or current account' : undefined}>
            {(control) => (
              <Select {...control} value={accountId} onChange={(event) => setAccountId(event.target.value)}>
                <option value="">Choose</option>
                {accounts.data?.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.accountNumber} · {account.title}
                  </option>
                ))}
              </Select>
            )}
          </FormField>
          <FormField
            label="Amount"
            required
            hint={terms ? `${terms.currency} ${terms.minAmount} to ${terms.maxAmount}` : undefined}
          >
            {(control) => <Input {...control} inputMode="decimal" placeholder="5000.00" value={amount} onChange={(event) => setAmount(event.target.value.trim())} />}
          </FormField>
          <FormField
            label="Installments"
            required
            hint={terms ? `${terms.minInstallments} to ${terms.maxInstallments}, ${terms.repaymentFrequency.toLowerCase()}` : undefined}
          >
            {(control) => <Input {...control} inputMode="numeric" value={installments} onChange={(event) => setInstallments(event.target.value.trim())} />}
          </FormField>
          <FormField label="Monthly income">
            {(control) => <Input {...control} inputMode="decimal" value={monthlyIncome} onChange={(event) => setMonthlyIncome(event.target.value.trim())} />}
          </FormField>
          <FormField label="Monthly expenses">
            {(control) => <Input {...control} inputMode="decimal" value={monthlyExpenses} onChange={(event) => setMonthlyExpenses(event.target.value.trim())} />}
          </FormField>
          <FormField label="Other debt">
            {(control) => <Input {...control} inputMode="decimal" value={existingDebt} onChange={(event) => setExistingDebt(event.target.value.trim())} />}
          </FormField>
        </div>
        <FormField label="Purpose" required>
          {(control) => <Textarea {...control} maxLength={300} value={purpose} onChange={(event) => setPurpose(event.target.value)} />}
        </FormField>
        {preview.data && terms ? <PreviewSummary preview={preview.data} currency={terms.currency} /> : null}
      </div>
    </Modal>
  );
}
