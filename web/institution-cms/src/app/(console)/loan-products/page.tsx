'use client';

import { bff, Permission, type DelinquencyBand, type LoanProduct, type LoanTermsRequest } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, Card, CardContent, CardHeader, CardTitle, Checkbox, DataTable, DetailList, FormField, Input, Modal, Money, PageHeader, Select, StatusBadge, formatDate, humanize } from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus, Trash2 } from 'lucide-react';
import { useState } from 'react';
import { useCan } from '@/lib/me';
import { AMOUNT } from '@/lib/schemas';

const RATE = /^(0|[1-9][0-9]{0,3})(\.[0-9]{1,6})?$/;

export default function LoanProductsPage() {
  const canManage = useCan(Permission.productManage);
  const [creating, setCreating] = useState(false);
  const [selected, setSelected] = useState<string | null>(null);
  const products = useQuery({ queryKey: ['loan-products'], queryFn: ({ signal }) => bff<LoanProduct[]>('/loan-products', { signal }) });

  const columns: ColumnDef<LoanProduct, unknown>[] = [
    { header: 'Code', cell: ({ row }) => <span className="font-mono text-sm">{row.original.code}</span> },
    { header: 'Name', cell: ({ row }) => row.original.name },
    {
      header: 'Published terms',
      cell: ({ row }) => {
        const terms = row.original.currentVersion;
        return terms ? `${terms.annualRate}% ${humanize(terms.interestMethod)}, ${terms.minInstallments}–${terms.maxInstallments} × ${terms.repaymentFrequency.toLowerCase()}` : 'Not published';
      },
    },
    {
      header: 'Amounts',
      cell: ({ row }) => {
        const terms = row.original.currentVersion;
        return terms ? (
          <>
            <Money amount={terms.minAmount} currency={terms.currency} /> – <Money amount={terms.maxAmount} currency={terms.currency} />
          </>
        ) : '—';
      },
    },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <>
      <PageHeader
        title="Loan products"
        description="Terms are versioned: applications use the published version and every loan keeps the terms it was disbursed on."
        actions={
          canManage ? (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden="true" />
              New loan product
            </Button>
          ) : null
        }
      />
      {products.isError ? <Alert tone="danger" className="mb-4">{errorMessage(products.error)}</Alert> : null}
      <DataTable caption="Loan products" columns={columns} data={products.data} loading={products.isLoading} onRowClick={(product) => setSelected(product.id)} getRowId={(product) => product.id} emptyTitle="No loan products" />
      <BandsCard canManage={canManage} />
      {creating ? <NewProductDialog onClose={() => setCreating(false)} /> : null}
      {selected ? <ProductDialog productId={selected} canManage={canManage} onClose={() => setSelected(null)} /> : null}
    </>
  );
}

function NewProductDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [terms, setTerms] = useState({
    currency: 'GHS',
    minAmount: '',
    maxAmount: '',
    minInstallments: '1',
    maxInstallments: '12',
    interestMethod: 'DECLINING_BALANCE_EQUAL_INSTALLMENT',
    annualRate: '',
    dayCount: 'ACTUAL_365F',
    repaymentFrequency: 'MONTHLY',
    processingFeeRate: '0',
    processingFeeFlat: '0',
    penaltyRate: '0',
    penaltyGraceDays: '0',
    requiredGuarantors: '0',
    collateralCoverage: '0',
    secondApprovalAbove: '',
  });
  const set = (field: keyof typeof terms) => (value: string) => setTerms((current) => ({ ...current, [field]: value }));
  const whole = (value: string) => /^[0-9]{1,3}$/.test(value);
  const valid =
    /^[A-Z0-9][A-Z0-9_-]{1,29}$/.test(code) && !!name.trim() && AMOUNT.test(terms.minAmount) && AMOUNT.test(terms.maxAmount)
    && RATE.test(terms.annualRate) && RATE.test(terms.processingFeeRate) && AMOUNT.test(terms.processingFeeFlat) && RATE.test(terms.penaltyRate)
    && RATE.test(terms.collateralCoverage) && whole(terms.minInstallments) && whole(terms.maxInstallments) && whole(terms.penaltyGraceDays)
    && whole(terms.requiredGuarantors) && (terms.secondApprovalAbove === '' || AMOUNT.test(terms.secondApprovalAbove));
  const create = useMutation({
    mutationFn: () => {
      const body: LoanTermsRequest = {
        currency: terms.currency,
        minAmount: terms.minAmount,
        maxAmount: terms.maxAmount,
        minInstallments: Number(terms.minInstallments),
        maxInstallments: Number(terms.maxInstallments),
        interestMethod: terms.interestMethod as LoanTermsRequest['interestMethod'],
        annualRate: terms.annualRate,
        dayCount: terms.dayCount as LoanTermsRequest['dayCount'],
        repaymentFrequency: terms.repaymentFrequency as LoanTermsRequest['repaymentFrequency'],
        processingFeeRate: terms.processingFeeRate,
        processingFeeFlat: terms.processingFeeFlat,
        penaltyRate: terms.penaltyRate,
        penaltyGraceDays: Number(terms.penaltyGraceDays),
        requiredGuarantors: Number(terms.requiredGuarantors),
        collateralCoverage: terms.collateralCoverage,
        ...(terms.secondApprovalAbove ? { secondApprovalAbove: terms.secondApprovalAbove } : {}),
      };
      return bff<LoanProduct>('/loan-products', { body: { code, name: name.trim(), terms: body } });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['loan-products'] });
      onClose();
    },
  });
  const text = (label: string, field: keyof typeof terms, hint?: string) => (
    <FormField label={label} hint={hint}>
      {(control) => <Input {...control} value={terms[field]} onChange={(event) => set(field)(event.target.value.trim())} />}
    </FormField>
  );

  return (
    <Modal
      open
      onClose={onClose}
      busy={create.isPending}
      className="max-w-3xl"
      title="New loan product"
      description="The product starts with a draft version; publish it to take applications. GL accounts default to the institution's loan accounts."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={create.isPending}>
            Cancel
          </Button>
          <Button onClick={() => create.mutate()} disabled={!valid} loading={create.isPending}>
            Create draft
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {create.isError ? <Alert tone="danger">{errorMessage(create.error)}</Alert> : null}
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label="Code" required>
            {(control) => <Input {...control} value={code} onChange={(event) => setCode(event.target.value.trim().toUpperCase())} />}
          </FormField>
          <FormField label="Name" required className="sm:col-span-2">
            {(control) => <Input {...control} value={name} onChange={(event) => setName(event.target.value)} />}
          </FormField>
          {text('Currency', 'currency')}
          {text('Minimum amount', 'minAmount')}
          {text('Maximum amount', 'maxAmount')}
          {text('Fewest installments', 'minInstallments')}
          {text('Most installments', 'maxInstallments')}
          {text('Annual rate (%)', 'annualRate')}
          <FormField label="Interest method">
            {(control) => (
              <Select {...control} value={terms.interestMethod} onChange={(event) => set('interestMethod')(event.target.value)}>
                <option value="DECLINING_BALANCE_EQUAL_INSTALLMENT">Declining balance, equal installments</option>
                <option value="DECLINING_BALANCE_EQUAL_PRINCIPAL">Declining balance, equal principal</option>
                <option value="FLAT">Flat</option>
              </Select>
            )}
          </FormField>
          <FormField label="Day count">
            {(control) => (
              <Select {...control} value={terms.dayCount} onChange={(event) => set('dayCount')(event.target.value)}>
                <option value="ACTUAL_365F">Actual/365</option>
                <option value="ACTUAL_360">Actual/360</option>
                <option value="THIRTY_360">30/360</option>
              </Select>
            )}
          </FormField>
          <FormField label="Repayments">
            {(control) => (
              <Select {...control} value={terms.repaymentFrequency} onChange={(event) => set('repaymentFrequency')(event.target.value)}>
                {['DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY'].map((frequency) => (
                  <option key={frequency} value={frequency}>
                    {humanize(frequency)}
                  </option>
                ))}
              </Select>
            )}
          </FormField>
          {text('Processing fee (%)', 'processingFeeRate')}
          {text('Processing fee (flat)', 'processingFeeFlat')}
          {text('Penalty rate (% a year)', 'penaltyRate')}
          {text('Penalty grace (days)', 'penaltyGraceDays')}
          {text('Guarantors required', 'requiredGuarantors')}
          {text('Collateral cover (%)', 'collateralCoverage', 'Verified forced-sale value as % of the loan')}
          {text('Second approval above', 'secondApprovalAbove', 'Empty: one approver')}
        </div>
      </div>
    </Modal>
  );
}

function ProductDialog({ productId, canManage, onClose }: { productId: string; canManage: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const product = useQuery({ queryKey: ['loan-products', productId], queryFn: ({ signal }) => bff<LoanProduct>(`/loan-products/${productId}`, { signal }) });
  const refresh = (data: LoanProduct) => {
    queryClient.setQueryData(['loan-products', productId], data);
    void queryClient.invalidateQueries({ queryKey: ['loan-products'] });
  };
  const publish = useMutation({
    mutationFn: (versionId: string) => bff<LoanProduct>(`/loan-products/${productId}/versions/${versionId}/publish`, { method: 'POST' }),
    onSuccess: refresh,
  });
  const draft = useMutation({ mutationFn: () => bff<LoanProduct>(`/loan-products/${productId}/versions`, { method: 'POST' }), onSuccess: refresh });
  const data = product.data;
  const hasDraft = data?.versions.some((version) => version.status === 'DRAFT');

  return (
    <Modal open onClose={onClose} className="max-w-3xl" title={data ? `${data.name} (${data.code})` : 'Loan product'} footer={<Button variant="outline" onClick={onClose}>Close</Button>}>
      <div className="grid gap-4">
        {product.isError ? <Alert tone="danger">{errorMessage(product.error)}</Alert> : null}
        {publish.isError ? <Alert tone="danger">{errorMessage(publish.error)}</Alert> : null}
        {draft.isError ? <Alert tone="danger">{errorMessage(draft.error)}</Alert> : null}
        {data?.versions.map((version) => (
          <Card key={version.id}>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                Version {version.versionNo} <StatusBadge status={version.status} />
              </CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3">
              <DetailList
                columns={3}
                items={[
                  { label: 'Amounts', value: <><Money amount={version.minAmount} currency={version.currency} /> – <Money amount={version.maxAmount} currency={version.currency} /></> },
                  { label: 'Installments', value: `${version.minInstallments}–${version.maxInstallments} × ${version.repaymentFrequency.toLowerCase()}` },
                  { label: 'Interest', value: `${version.annualRate}% ${humanize(version.interestMethod)} (${humanize(version.dayCount)})` },
                  { label: 'Processing fee', value: <>{version.processingFeeRate}% + <Money amount={version.processingFeeFlat} currency={version.currency} /></> },
                  { label: 'Penalty', value: `${version.penaltyRate}% a year after ${version.penaltyGraceDays} days` },
                  { label: 'Security', value: `${version.requiredGuarantors} guarantors, ${version.collateralCoverage}% collateral` },
                  { label: 'Second approval above', value: version.secondApprovalAbove ? <Money amount={version.secondApprovalAbove} currency={version.currency} /> : 'Never' },
                  { label: 'Repayments settle', value: version.allocationOrder.toLowerCase().replaceAll(',', ', ') },
                  { label: 'Published', value: formatDate(version.publishedAt) },
                ]}
              />
              {canManage && version.status === 'DRAFT' ? (
                <div>
                  <Button size="sm" onClick={() => publish.mutate(version.id)} loading={publish.isPending}>
                    Publish version {version.versionNo}
                  </Button>
                </div>
              ) : null}
            </CardContent>
          </Card>
        ))}
        {canManage && data && !hasDraft ? (
          <div>
            <Button size="sm" variant="outline" onClick={() => draft.mutate()} loading={draft.isPending}>
              Start a new draft from the latest terms
            </Button>
          </div>
        ) : null}
      </div>
    </Modal>
  );
}

function BandsCard({ canManage }: { canManage: boolean }) {
  const queryClient = useQueryClient();
  const bands = useQuery({ queryKey: ['loan-portfolio', 'bands'], queryFn: ({ signal }) => bff<DelinquencyBand[]>('/loan-portfolio/delinquency-bands', { signal }) });
  const [editing, setEditing] = useState<DelinquencyBand[] | null>(null);
  const save = useMutation({
    mutationFn: (ladder: DelinquencyBand[]) => bff<DelinquencyBand[]>('/loan-portfolio/delinquency-bands', { method: 'PUT', body: { bands: ladder } }),
    onSuccess: (saved) => {
      queryClient.setQueryData(['loan-portfolio', 'bands'], saved);
      setEditing(null);
    },
  });
  const update = (index: number, change: Partial<DelinquencyBand>) =>
    setEditing((current) => current?.map((band, position) => (position === index ? { ...band, ...change } : band)) ?? null);
  const valid = editing?.every((band) => /^[A-Z][A-Z0-9_]{1,19}$/.test(band.code) && band.name.trim() && Number.isInteger(band.minDays) && band.minDays >= 0 && RATE.test(band.provisionRate));

  return (
    <Card className="mt-6">
      <CardHeader>
        <CardTitle className="text-base">Delinquency bands</CardTitle>
        <p className="text-sm text-muted-foreground">
          From each band's days past due, loans are provisioned at its rate of their principal; where accrual is suspended, uncollected interest is held in
          suspense instead of income. These are the institution's settings to review against its regulator's rules.
        </p>
      </CardHeader>
      <CardContent className="grid gap-3">
        {bands.isError ? <Alert tone="danger">{errorMessage(bands.error)}</Alert> : null}
        {save.isError ? <Alert tone="danger">{errorMessage(save.error)}</Alert> : null}
        {editing ? (
          <>
            {editing.map((band, index) => (
              <div key={index} className="grid items-end gap-2 sm:grid-cols-6">
                <FormField label="Code">{(control) => <Input {...control} value={band.code} onChange={(event) => update(index, { code: event.target.value.trim().toUpperCase() })} />}</FormField>
                <FormField label="Name">{(control) => <Input {...control} value={band.name} onChange={(event) => update(index, { name: event.target.value })} />}</FormField>
                <FormField label="From days late">{(control) => <Input {...control} inputMode="numeric" value={String(band.minDays)} onChange={(event) => update(index, { minDays: Number(event.target.value.trim()) })} />}</FormField>
                <FormField label="Provision (%)">{(control) => <Input {...control} value={band.provisionRate} onChange={(event) => update(index, { provisionRate: event.target.value.trim() })} />}</FormField>
                <Checkbox label="Suspend accrual" checked={band.suspendAccrual} onChange={(event) => update(index, { suspendAccrual: event.target.checked })} />
                <Button size="sm" variant="outline" aria-label={`Remove ${band.code || 'band'}`} onClick={() => setEditing((current) => current?.filter((_, position) => position !== index) ?? null)}>
                  <Trash2 aria-hidden="true" />
                </Button>
              </div>
            ))}
            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={() => setEditing((current) => [...(current ?? []), { code: '', name: '', minDays: 0, provisionRate: '0', suspendAccrual: false }])}>
                Add band
              </Button>
              <Button size="sm" disabled={!valid} loading={save.isPending} onClick={() => editing && save.mutate(editing)}>
                Save bands
              </Button>
              <Button size="sm" variant="outline" onClick={() => setEditing(null)}>
                Cancel
              </Button>
            </div>
          </>
        ) : (
          <>
            <ul className="grid gap-1 text-sm">
              {bands.data?.map((band) => (
                <li key={band.code}>
                  <strong>{band.name}</strong> from {band.minDays} days late: {band.provisionRate}% provision{band.suspendAccrual ? ', accrual suspended' : ''}
                </li>
              ))}
            </ul>
            {canManage && bands.data ? (
              <div>
                <Button size="sm" variant="outline" onClick={() => setEditing(bands.data.map((band) => ({ ...band })))}>
                  Edit bands
                </Button>
              </div>
            ) : null}
          </>
        )}
      </CardContent>
    </Card>
  );
}
