'use client';

import {
  bff,
  newIdempotencyKey,
  Permission,
  type Approval,
  type LoanCollectionActivity,
  type LoanDetail,
  type LoanRepayment,
  type RecoveryReceipt,
  type RepaymentReceipt,
} from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, DetailList, FormField, Input, Modal, Money, Select, StatusBadge, Tabs, Textarea, formatDate, humanize } from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { useCan } from '@/lib/me';
import { AMOUNT } from '@/lib/schemas';
import { InstallmentTable } from './schedule-tables';

type Tab = 'schedule' | 'repayments' | 'collections' | 'actions';

/** A loan: balances from its ledger accounts, schedule, repayments, collections and what staff may do with it. */
export function LoanDialog({ loanId, initialTab = 'schedule', onClose }: { loanId: string; initialTab?: Tab; onClose: () => void }) {
  const [tab, setTab] = useState<Tab>(initialTab);
  const detail = useQuery({ queryKey: ['loans', loanId], queryFn: ({ signal }) => bff<LoanDetail>(`/loans/${loanId}`, { signal }) });
  const loan = detail.data?.loan;

  return (
    <Modal
      open
      onClose={onClose}
      className="max-w-5xl"
      title={loan ? `${loan.loanNumber} · ${loan.customerName ?? ''}` : 'Loan'}
      description={loan ? <StatusBadge status={loan.status} /> : null}
      footer={<Button variant="outline" onClick={onClose}>Close</Button>}
    >
      <div className="grid gap-4">
        {detail.isError ? <Alert tone="danger">{errorMessage(detail.error)}</Alert> : null}
        {loan ? (
          <DetailList
            columns={3}
            items={[
              { label: 'Principal outstanding', value: <Money amount={loan.principalOutstanding} currency={loan.currency} /> },
              { label: 'Interest earned, unpaid', value: <Money amount={loan.interestReceivable} currency={loan.currency} /> },
              { label: 'Penalties', value: <Money amount={loan.penaltyReceivable} currency={loan.currency} /> },
              { label: 'In arrears', value: <Money amount={loan.arrears} currency={loan.currency} /> },
              { label: 'Next due', value: loan.nextDueDate ? <>{formatDate(loan.nextDueDate)} · <Money amount={loan.nextDueAmount ?? '0'} currency={loan.currency} /></> : '—' },
              { label: 'Pays off today', value: detail.data?.payoff ? <Money amount={detail.data.payoff.total} currency={loan.currency} /> : '—' },
              { label: 'Days past due', value: `${loan.daysPastDue}${loan.delinquencyBand ? ` · ${humanize(loan.delinquencyBand)}` : ''}${loan.nonAccrual ? ' · non-accrual' : ''}` },
              { label: 'Provision held', value: <Money amount={loan.provisionHeld} currency={loan.currency} /> },
              { label: 'Product', value: loan.productName ?? loan.productCode },
              { label: 'Terms', value: `${loan.annualRate}% ${humanize(loan.interestMethod)}, ${loan.installments} × ${loan.repaymentFrequency.toLowerCase()}` },
              { label: 'Disbursed', value: <>{formatDate(loan.disbursementDate)} · <Money amount={loan.principal} currency={loan.currency} /></> },
              { label: 'Matures', value: formatDate(loan.maturityDate) },
              ...(loan.bandFloor ? [{ label: 'Band held', value: `${humanize(loan.bandFloor)} until ${formatDate(loan.bandFloorUntil)}` }] : []),
              ...(loan.status === 'WRITTEN_OFF'
                ? [
                    { label: 'Written off', value: <Money amount={loan.writtenOff} currency={loan.currency} /> },
                    { label: 'Recovered', value: <Money amount={loan.recovered} currency={loan.currency} /> },
                  ]
                : []),
            ]}
          />
        ) : null}
        <Tabs
          tabs={[
            { id: 'schedule', label: `Schedule${loan && loan.scheduleVersion > 1 ? ` (version ${loan.scheduleVersion})` : ''}` },
            { id: 'repayments', label: 'Repayments' },
            { id: 'collections', label: 'Collections' },
            { id: 'actions', label: 'Actions' },
          ]}
          active={tab}
          onChange={(id) => setTab(id as Tab)}
        />
        {tab === 'schedule' && detail.data && loan ? <InstallmentTable installments={detail.data.schedule} currency={loan.currency} /> : null}
        {tab === 'repayments' && detail.data && loan ? <Repayments detail={detail.data} /> : null}
        {tab === 'collections' && loan ? <Collections loanId={loanId} currency={loan.currency} closed={loan.status === 'CLOSED'} /> : null}
        {tab === 'actions' && detail.data ? <Actions detail={detail.data} /> : null}
      </div>
    </Modal>
  );
}

function Repayments({ detail }: { detail: LoanDetail }) {
  const currency = detail.loan.currency;
  const columns: ColumnDef<LoanRepayment, unknown>[] = [
    { header: 'Date', cell: ({ row }) => formatDate(row.original.businessDate) },
    { header: 'Source', cell: ({ row }) => humanize(row.original.source) },
    { header: 'Amount', cell: ({ row }) => <Money amount={row.original.amount} currency={currency} /> },
    { header: 'Penalty', cell: ({ row }) => <Money amount={row.original.penalty} currency={currency} /> },
    { header: 'Interest', cell: ({ row }) => <Money amount={row.original.interest} currency={currency} /> },
    { header: 'Principal', cell: ({ row }) => <Money amount={row.original.principal} currency={currency} /> },
  ];
  return (
    <div className="grid gap-3">
      <DataTable caption="Repayments" columns={columns} data={detail.repayments} getRowId={(row) => row.id} emptyTitle="No repayments yet" />
      {detail.restructures.map((restructure) => (
        <p key={restructure.id} className="text-sm">
          Restructured on {formatDate(restructure.businessDate)}: <Money amount={restructure.principal} currency={currency} /> over {restructure.installments}{' '}
          installments, <Money amount={restructure.interestCarried} currency={currency} /> interest carried. {restructure.reason}
        </p>
      ))}
      {detail.recoveries.map((recovery) => (
        <p key={recovery.id} className="text-sm">
          Recovered {formatDate(recovery.businessDate)}: <Money amount={recovery.amount} currency={currency} /> ({humanize(recovery.source)})
        </p>
      ))}
    </div>
  );
}

function Collections({ loanId, currency, closed }: { loanId: string; currency: string; closed: boolean }) {
  const queryClient = useQueryClient();
  const canCollect = useCan(Permission.loanCollect);
  const [type, setType] = useState('CALL');
  const [note, setNote] = useState('');
  const [promisedAmount, setPromisedAmount] = useState('');
  const [promisedDate, setPromisedDate] = useState('');
  const path = `/loans/${loanId}/collection-activities`;
  const activities = useQuery({ queryKey: ['loans', loanId, 'activities'], queryFn: ({ signal }) => bff<LoanCollectionActivity[]>(path, { signal }) });
  const promise = type === 'PROMISE';
  const record = useMutation({
    mutationFn: () => bff<LoanCollectionActivity>(path, { body: { type, note: note.trim(), ...(promise ? { promisedAmount, promisedDate } : {}) } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['loans', loanId, 'activities'] });
      void queryClient.invalidateQueries({ queryKey: ['collections'] });
      setNote('');
      setPromisedAmount('');
      setPromisedDate('');
    },
  });
  const valid = !!note.trim() && (!promise || (AMOUNT.test(promisedAmount) && /[1-9]/.test(promisedAmount) && !!promisedDate));
  const columns: ColumnDef<LoanCollectionActivity, unknown>[] = [
    { header: 'Date', cell: ({ row }) => formatDate(row.original.businessDate) },
    { header: 'Type', cell: ({ row }) => humanize(row.original.type) },
    { header: 'Note', cell: ({ row }) => <span className="text-sm">{row.original.note}</span> },
    { header: 'Days late', cell: ({ row }) => row.original.daysPastDue },
    {
      header: 'Promise',
      cell: ({ row }) =>
        row.original.promisedAmount ? (
          <>
            <Money amount={row.original.promisedAmount} currency={currency} /> by {formatDate(row.original.promisedDate)}{' '}
            {row.original.promiseStatus ? <StatusBadge status={row.original.promiseStatus} /> : null}
          </>
        ) : '—',
    },
  ];
  return (
    <div className="grid gap-3">
      {activities.isError ? <Alert tone="danger">{errorMessage(activities.error)}</Alert> : null}
      {record.isError ? <Alert tone="danger">{errorMessage(record.error)}</Alert> : null}
      {canCollect && !closed ? (
        <div className="grid gap-3 rounded-md border p-3 sm:grid-cols-4">
          <FormField label="Activity">
            {(control) => (
              <Select {...control} value={type} onChange={(event) => setType(event.target.value)}>
                {['CALL', 'VISIT', 'SMS', 'LETTER', 'PROMISE', 'OTHER'].map((code) => (
                  <option key={code} value={code}>
                    {code === 'PROMISE' ? 'Promise to pay' : humanize(code)}
                  </option>
                ))}
              </Select>
            )}
          </FormField>
          {promise ? (
            <>
              <FormField label="Amount promised" required>
                {(control) => <Input {...control} inputMode="decimal" value={promisedAmount} onChange={(event) => setPromisedAmount(event.target.value.trim())} />}
              </FormField>
              <FormField label="By" required>
                {(control) => <Input {...control} type="date" value={promisedDate} onChange={(event) => setPromisedDate(event.target.value)} />}
              </FormField>
            </>
          ) : null}
          <FormField label="Note" required className="sm:col-span-4">
            {(control) => <Textarea {...control} maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} />}
          </FormField>
          <div>
            <Button size="sm" disabled={!valid} loading={record.isPending} onClick={() => record.mutate()}>
              Record
            </Button>
          </div>
        </div>
      ) : null}
      <DataTable caption="Collection activity" columns={columns} data={activities.data} loading={activities.isLoading} getRowId={(row) => row.id} emptyTitle="Nothing recorded yet" />
    </div>
  );
}

function Actions({ detail }: { detail: LoanDetail }) {
  const queryClient = useQueryClient();
  const { loan } = detail;
  const canRepay = useCan(Permission.loanRepay);
  const canRestructure = useCan(Permission.loanRestructure);
  const canWriteOff = useCan(Permission.loanWriteoff);
  const [amount, setAmount] = useState('');
  const [source, setSource] = useState('ACCOUNT');
  const [paymentKey, setPaymentKey] = useState(newIdempotencyKey);
  const [installments, setInstallments] = useState('');
  const [holdBandDays, setHoldBandDays] = useState('90');
  const [firstDueDate, setFirstDueDate] = useState('');
  const [reason, setReason] = useState('');
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['loans'] });
    void queryClient.invalidateQueries({ queryKey: ['collections'] });
  };
  const written = loan.status === 'WRITTEN_OFF';
  const pay = useMutation<RepaymentReceipt | RecoveryReceipt>({
    mutationFn: () =>
      written
        ? bff<RecoveryReceipt>(`/loans/${loan.id}/recoveries`, { body: { amount, source }, idempotencyKey: paymentKey })
        : bff<RepaymentReceipt>(`/loans/${loan.id}/repayments`, { body: { amount, source }, idempotencyKey: paymentKey }),
    onSuccess: () => {
      refresh();
      setAmount('');
      setPaymentKey(newIdempotencyKey());
    },
  });
  const restructure = useMutation({
    mutationFn: () =>
      bff<Approval>(`/loans/${loan.id}/restructure`, {
        body: { installments: Number(installments), holdBandDays: Number(holdBandDays), reason: reason.trim(), ...(firstDueDate ? { firstDueDate } : {}) },
      }),
    onSuccess: refresh,
  });
  const writeOff = useMutation({
    mutationFn: () => bff<Approval>(`/loans/${loan.id}/write-off`, { body: { reason: reason.trim() } }),
    onSuccess: refresh,
  });
  const failure = [pay, restructure, writeOff].find((mutation) => mutation.isError)?.error;
  const amountValid = AMOUNT.test(amount) && /[1-9]/.test(amount);
  const count = Number(installments);
  const hold = Number(holdBandDays);
  const restructureValid = Number.isInteger(count) && count >= 1 && count <= 520 && Number.isInteger(hold) && hold >= 0 && hold <= 730 && !!reason.trim();

  if (loan.status === 'CLOSED') {
    return <p className="text-sm text-muted-foreground">The loan was repaid on {formatDate(loan.closedOn)}.</p>;
  }
  return (
    <div className="grid gap-4">
      {failure ? <Alert tone="danger">{errorMessage(failure)}</Alert> : null}
      {pay.isSuccess ? <Alert tone="success">{'settled' in pay.data && pay.data.settled ? 'The loan is repaid in full.' : 'Payment received.'}</Alert> : null}
      {restructure.isSuccess ? <Alert tone="success">Restructure sent for approval.</Alert> : null}
      {writeOff.isSuccess ? <Alert tone="success">Write-off sent for approval.</Alert> : null}
      {canRepay ? (
        <div className="grid gap-3 rounded-md border p-3 sm:grid-cols-3">
          <FormField label={written ? 'Amount recovered' : 'Repayment'} required>
            {(control) => <Input {...control} inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value.trim())} />}
          </FormField>
          <FormField label="From">
            {(control) => (
              <Select {...control} value={source} onChange={(event) => setSource(event.target.value)}>
                <option value="ACCOUNT">The borrower's account</option>
                <option value="CASH">Cash at my till</option>
              </Select>
            )}
          </FormField>
          <div className="flex items-end gap-2">
            <Button size="sm" disabled={!amountValid} loading={pay.isPending} onClick={() => pay.mutate()}>
              {written ? 'Record recovery' : 'Take repayment'}
            </Button>
            {!written && detail.payoff ? (
              <Button size="sm" variant="outline" onClick={() => setAmount(detail.payoff?.total ?? '')}>
                Pay off <Money amount={detail.payoff.total} currency={loan.currency} />
              </Button>
            ) : null}
          </div>
        </div>
      ) : null}
      {!written && (canRestructure || canWriteOff) ? (
        <div className="grid gap-3 rounded-md border p-3">
          <p className="text-sm text-muted-foreground">Restructures and write-offs need a second person to approve them on the Approvals page.</p>
          <FormField label="Reason" required>
            {(control) => <Textarea {...control} maxLength={300} value={reason} onChange={(event) => setReason(event.target.value)} />}
          </FormField>
          {canRestructure ? (
            <div className="grid gap-3 sm:grid-cols-4">
              <FormField label="New installments" required>
                {(control) => <Input {...control} inputMode="numeric" value={installments} onChange={(event) => setInstallments(event.target.value.trim())} />}
              </FormField>
              <FormField label="First due date">
                {(control) => <Input {...control} type="date" value={firstDueDate} onChange={(event) => setFirstDueDate(event.target.value)} />}
              </FormField>
              <FormField label="Keep band for (days)" required>
                {(control) => <Input {...control} inputMode="numeric" value={holdBandDays} onChange={(event) => setHoldBandDays(event.target.value.trim())} />}
              </FormField>
              <div className="flex items-end">
                <Button size="sm" variant="outline" disabled={!restructureValid} loading={restructure.isPending} onClick={() => restructure.mutate()}>
                  Ask to restructure
                </Button>
              </div>
            </div>
          ) : null}
          {canWriteOff ? (
            <div>
              <Button size="sm" variant="destructive" disabled={!reason.trim()} loading={writeOff.isPending} onClick={() => writeOff.mutate()}>
                Ask to write off
              </Button>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
