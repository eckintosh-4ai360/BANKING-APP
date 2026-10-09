'use client';

import { bff, Permission, query, type AccountSummary, type CustomerSummary, type Page, type SusuFrequency, type SusuPlan, type SusuPlanDetail } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, DetailList, FormField, Input, Modal, Money, PageHeader, Select, StatusBadge, Textarea, formatDate } from '@banking/ui';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useCan } from '@/lib/me';
import { AMOUNT } from '@/lib/schemas';

export default function SusuPage() {
  const canManage = useCan(Permission.susuManage);
  const [status, setStatus] = useState('ACTIVE');
  const [page, setPage] = useState(0);
  const [opening, setOpening] = useState(false);
  const [selected, setSelected] = useState<SusuPlan | null>(null);
  const plans = useQuery({
    queryKey: ['susu', 'plans', status, page],
    queryFn: ({ signal }) => bff<Page<SusuPlan>>(`/susu/plans${query({ status, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<SusuPlan, unknown>[] = [
    { header: 'Plan', cell: ({ row }) => <span className="font-mono text-sm">{row.original.planNumber}</span> },
    { header: 'Contribution', cell: ({ row }) => <Money amount={row.original.contributionAmount} currency={row.original.currency} /> },
    { header: 'Frequency', cell: ({ row }) => row.original.frequencyCode },
    { header: 'Cycle', cell: ({ row }) => `${row.original.currentCycle} · ${row.original.cycleLength} contributions` },
    { header: 'Paid', cell: ({ row }) => <Money amount={row.original.totalPaid} currency={row.original.currency} /> },
    {
      header: 'Arrears',
      cell: ({ row }) => (row.original.missed > 0 ? <span className="text-destructive"><Money amount={row.original.arrears} currency={row.original.currency} /> ({row.original.missed})</span> : '—'),
    },
    { header: 'Next due', cell: ({ row }) => formatDate(row.original.nextDue) },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <>
      <PageHeader
        title="Susu"
        description="Customers contribute a fixed amount at a fixed frequency; field officers collect it. At the end of each cycle the collector's commission is charged on what was paid."
        actions={
          canManage ? (
            <Button onClick={() => setOpening(true)}>
              <Plus aria-hidden="true" />
              Open plan
            </Button>
          ) : null
        }
      />
      <FormField label="Status" className="mb-4 w-48">
        {(control) => (
          <Select {...control} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="ACTIVE">Running</option>
            <option value="COMPLETED">Completed</option>
            <option value="CANCELLED">Cancelled</option>
            <option value="">Any</option>
          </Select>
        )}
      </FormField>
      {plans.isError ? <Alert tone="danger" className="mb-4">{errorMessage(plans.error)}</Alert> : null}
      <DataTable caption="Susu plans" columns={columns} data={plans.data?.items} loading={plans.isLoading} pageInfo={plans.data} onPageChange={setPage} onRowClick={setSelected} getRowId={(plan) => plan.id} emptyTitle="No susu plans" />
      {opening ? <OpenPlanDialog onClose={() => setOpening(false)} /> : null}
      {selected ? <PlanDialog planId={selected.id} onClose={() => setSelected(null)} /> : null}
    </>
  );
}

function OpenPlanDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [accountId, setAccountId] = useState('');
  const [frequencyCode, setFrequencyCode] = useState('DAILY');
  const [contributionAmount, setContributionAmount] = useState('');
  const [cycleLength, setCycleLength] = useState('31');
  const [commissionContributions, setCommissionContributions] = useState('1');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const customers = useQuery({
    queryKey: ['customers', 'search', search],
    queryFn: ({ signal }) => bff<Page<CustomerSummary>>(`/customers${query({ q: search, size: 20 })}`, { signal }),
    enabled: search.trim().length >= 2,
  });
  const accounts = useQuery({
    queryKey: ['customers', customerId, 'accounts'],
    queryFn: ({ signal }) => bff<AccountSummary[]>(`/customers/${customerId}/accounts`, { signal }),
    enabled: !!customerId,
    select: (all) => all.filter((account) => account.productType === 'SUSU' && (account.status === 'ACTIVE' || account.status === 'PENDING')),
  });
  const frequencies = useQuery({ queryKey: ['susu', 'frequencies'], queryFn: ({ signal }) => bff<SusuFrequency[]>('/susu/frequencies', { signal }) });
  const cycle = Number(cycleLength);
  const commission = Number(commissionContributions);
  const valid = !!customerId && !!accountId && AMOUNT.test(contributionAmount) && /[1-9]/.test(contributionAmount)
    && Number.isInteger(cycle) && cycle >= 1 && cycle <= 366 && Number.isInteger(commission) && commission >= 0 && commission < cycle;
  const open = useMutation({
    mutationFn: () =>
      bff<SusuPlanDetail>('/susu/plans', {
        body: {
          customerId,
          accountId,
          frequencyCode,
          contributionAmount,
          cycleLength: cycle,
          commissionContributions: commission,
          ...(startDate ? { startDate } : {}),
          ...(endDate ? { endDate } : {}),
        },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['susu'] });
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={open.isPending}
      className="max-w-2xl"
      title="Open a susu plan"
      description="The first cycle is scheduled at once; end-of-day opens each next cycle."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={open.isPending}>
            Cancel
          </Button>
          <Button onClick={() => open.mutate()} disabled={!valid} loading={open.isPending}>
            Open plan
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {open.isError ? <Alert tone="danger">{errorMessage(open.error)}</Alert> : null}
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
          <FormField label="Susu account" required hint={customerId && accounts.data?.length === 0 ? 'The customer has no open susu account' : undefined}>
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
          <FormField label="Frequency" required>
            {(control) => (
              <Select {...control} value={frequencyCode} onChange={(event) => setFrequencyCode(event.target.value)}>
                {frequencies.data?.filter((frequency) => frequency.active).map((frequency) => (
                  <option key={frequency.code} value={frequency.code}>
                    {frequency.name}
                  </option>
                ))}
              </Select>
            )}
          </FormField>
          <FormField label="Contribution" required>
            {(control) => <Input {...control} inputMode="decimal" placeholder="10.00" value={contributionAmount} onChange={(event) => setContributionAmount(event.target.value.trim())} />}
          </FormField>
          <FormField label="Contributions per cycle" required>
            {(control) => <Input {...control} inputMode="numeric" value={cycleLength} onChange={(event) => setCycleLength(event.target.value.trim())} />}
          </FormField>
          <FormField label="Commission per cycle" required hint="Contributions kept by the collector each cycle">
            {(control) => <Input {...control} inputMode="numeric" value={commissionContributions} onChange={(event) => setCommissionContributions(event.target.value.trim())} />}
          </FormField>
          <FormField label="Start date" hint="Today's business date when empty">
            {(control) => <Input {...control} type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />}
          </FormField>
          <FormField label="End date" hint="Open-ended when empty">
            {(control) => <Input {...control} type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />}
          </FormField>
        </div>
      </div>
    </Modal>
  );
}

function PlanDialog({ planId, onClose }: { planId: string; onClose: () => void }) {
  const canManage = useCan(Permission.susuManage);
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const detail = useQuery({ queryKey: ['susu', 'plan', planId], queryFn: ({ signal }) => bff<SusuPlanDetail>(`/susu/plans/${planId}`, { signal }) });
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['susu'] });
  const cancel = useMutation({
    mutationFn: () => bff<SusuPlanDetail>(`/susu/plans/${planId}/cancel`, { body: { reason: reason.trim(), version: detail.data?.plan.version } }),
    onSuccess: refresh,
  });
  const waive = useMutation({
    mutationFn: (sequenceNo: number) => bff<SusuPlanDetail>(`/susu/plans/${planId}/contributions/${sequenceNo}/waive`, { body: { reason: reason.trim() } }),
    onSuccess: refresh,
  });
  const plan = detail.data?.plan;

  return (
    <Modal open onClose={onClose} className="max-w-3xl" title={plan?.planNumber ?? 'Susu plan'} description={plan ? <StatusBadge status={plan.status} /> : null} footer={<Button variant="outline" onClick={onClose}>Close</Button>}>
      <div className="grid gap-4">
        {detail.isError ? <Alert tone="danger">{errorMessage(detail.error)}</Alert> : null}
        {cancel.isError ? <Alert tone="danger">{errorMessage(cancel.error)}</Alert> : null}
        {waive.isError ? <Alert tone="danger">{errorMessage(waive.error)}</Alert> : null}
        {plan ? (
          <DetailList
            columns={3}
            items={[
              { label: 'Contribution', value: <Money amount={plan.contributionAmount} currency={plan.currency} /> },
              { label: 'Frequency', value: plan.frequencyCode },
              { label: 'Cycle', value: `${plan.currentCycle} (${plan.cycleLength} contributions, ${plan.commissionContributions} commission)` },
              { label: 'Started', value: formatDate(plan.startDate) },
              { label: 'Ends', value: plan.endDate ? formatDate(plan.endDate) : 'Open-ended' },
              { label: 'Paid', value: <Money amount={plan.totalPaid} currency={plan.currency} /> },
            ]}
          />
        ) : null}
        {detail.data?.commissions.length ? (
          <p className="text-sm">
            Commission charged:{' '}
            {detail.data.commissions.map((commission) => (
              <span key={commission.cycleNo} className="mr-2">
                cycle {commission.cycleNo}: <Money amount={commission.amountCharged} currency={plan?.currency ?? 'GHS'} />
              </span>
            ))}
          </p>
        ) : null}
        <div className="grid max-h-72 grid-cols-2 gap-1 overflow-y-auto sm:grid-cols-4">
          {detail.data?.contributions.map((contribution) => (
            <div key={contribution.sequenceNo} className="flex items-center justify-between gap-1 rounded border px-2 py-1 text-xs">
              <span>
                #{contribution.sequenceNo} · {formatDate(contribution.dueDate)}
              </span>
              {canManage && (contribution.status === 'MISSED' || contribution.status === 'EXPECTED') && reason.trim() ? (
                <button type="button" className="underline" onClick={() => waive.mutate(contribution.sequenceNo)}>
                  waive
                </button>
              ) : (
                <StatusBadge status={contribution.status} />
              )}
            </div>
          ))}
        </div>
        {canManage && plan?.status === 'ACTIVE' ? (
          <div className="grid gap-2">
            <FormField label="Reason" hint="Needed to waive a contribution or cancel the plan">
              {(control) => <Textarea {...control} maxLength={300} value={reason} onChange={(event) => setReason(event.target.value)} />}
            </FormField>
            <div>
              <Button variant="destructive" size="sm" disabled={!reason.trim()} onClick={() => cancel.mutate()} loading={cancel.isPending}>
                Cancel plan
              </Button>
            </div>
          </div>
        ) : null}
      </div>
    </Modal>
  );
}
