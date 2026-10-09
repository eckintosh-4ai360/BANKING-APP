'use client';

import { bff, type Charge, type Product, type ProductTermsInput, type ProductVersion } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import { Alert, Button, FormField, Input, Modal, Select, Textarea, humanize } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm, type Path } from 'react-hook-form';
import { useKycTiers } from '@/lib/queries';
import { productFormSchema, type ProductFormInput, type ProductFormValues } from '@/lib/schemas';

const PRODUCT_TYPES = ['SAVINGS', 'CURRENT', 'SUSU', 'FIXED_DEPOSIT', 'TARGET_SAVINGS'] as const;
const FLAT_FEES = { withdrawalFee: 'CASH_WITHDRAWAL', transferFee: 'TRANSFER_OUT' } as const;

/** Zero amounts show as empty fields, which mean "none" when saved. */
function editable(amount: string | null | undefined): string {
  if (!amount) {
    return '';
  }
  return /[1-9]/.test(amount) ? amount : '';
}

/** Form values for a new product, or for editing a draft version's terms. */
export function formDefaults(product?: Product, version?: ProductVersion): ProductFormInput {
  const flat = (event: Charge['event']) => version?.charges.find((charge) => charge.event === event && charge.calculation === 'FLAT');
  return {
    code: product?.code ?? '',
    name: product?.name ?? '',
    productType: product?.productType ?? 'SAVINGS',
    description: product?.description ?? '',
    currency: version?.currency ?? 'GHS',
    minOpeningBalance: editable(version?.minOpeningBalance),
    minOperatingBalance: editable(version?.minOperatingBalance),
    maxBalance: editable(version?.maxBalance),
    interestRate: version && /[1-9]/.test(version.interestRate) ? version.interestRate : '',
    requiredKycTier: version?.requiredKycTier ?? '',
    maxWithdrawalAmount: editable(version?.maxWithdrawalAmount),
    dailyWithdrawalLimit: editable(version?.dailyWithdrawalLimit),
    withdrawalFee: editable(flat('CASH_WITHDRAWAL')?.flatAmount),
    transferFee: editable(flat('TRANSFER_OUT')?.flatAmount),
  };
}

/**
 * The terms request for the backend. Flat withdrawal and transfer fees come from the form; any other charge already
 * on the version (percentage charges, deposit fees) is kept unchanged. Amounts stay strings.
 */
export function toTermsRequest(values: ProductFormValues, existing: Charge[] = []): ProductTermsInput {
  const handled = new Set<string>();
  const charges: Charge[] = [];
  for (const [field, event] of Object.entries(FLAT_FEES) as [keyof typeof FLAT_FEES, Charge['event']][]) {
    const current = existing.find((charge) => charge.event === event);
    if (current && current.calculation !== 'FLAT') {
      continue;
    }
    handled.add(event);
    const amount = values[field];
    if (amount) {
      charges.push({
        event,
        name: current?.name ?? (event === 'CASH_WITHDRAWAL' ? 'Withdrawal fee' : 'Transfer fee'),
        calculation: 'FLAT',
        flatAmount: amount,
        rate: null,
        minAmount: null,
        maxAmount: null,
      });
    }
  }
  charges.push(...existing.filter((charge) => !handled.has(charge.event)));
  return {
    currency: values.currency,
    minOpeningBalance: values.minOpeningBalance,
    minOperatingBalance: values.minOperatingBalance,
    maxBalance: values.maxBalance,
    interestRate: values.interestRate,
    requiredKycTier: values.requiredKycTier,
    allowOverdraft: false,
    maxWithdrawalAmount: values.maxWithdrawalAmount,
    dailyWithdrawalLimit: values.dailyWithdrawalLimit,
    charges,
  };
}

const TERM_FIELDS: Path<ProductFormValues>[] = [
  'currency',
  'minOpeningBalance',
  'minOperatingBalance',
  'maxBalance',
  'interestRate',
  'requiredKycTier',
  'maxWithdrawalAmount',
  'dailyWithdrawalLimit',
];

/**
 * Creates a product (with a draft first version) or edits the terms of a draft version. Published terms never change:
 * the server refuses it, so this dialog is only offered for drafts.
 */
export function ProductFormDialog({
  product,
  draft,
  onClose,
  onDone,
}: {
  product?: Product;
  draft?: ProductVersion;
  onClose: () => void;
  onDone: (product: Product) => void;
}) {
  const creating = !product;
  const tiers = useKycTiers();
  const form = useForm<ProductFormInput, unknown, ProductFormValues>({
    resolver: zodResolver(productFormSchema),
    defaultValues: formDefaults(product, draft),
  });
  const percentCharges = (draft?.charges ?? []).filter((charge) => charge.calculation !== 'FLAT');

  const save = useMutation({
    mutationFn: (values: ProductFormValues) => {
      const terms = toTermsRequest(values, draft?.charges);
      if (!product) {
        return bff<Product>('/products', {
          body: { code: values.code, name: values.name, productType: values.productType, description: values.description, terms },
        });
      }
      return bff<Product>(`/products/${product.id}/versions/${draft?.id}`, { method: 'PUT', body: terms });
    },
    onSuccess: (saved) => {
      onDone(saved);
      onClose();
    },
    onError: (error) => {
      applyFieldErrors(error, form.setError, ['code', 'name', 'description', ...TERM_FIELDS]);
      applyFieldErrors(
        error,
        form.setError,
        TERM_FIELDS.map((field) => `terms.${field}` as Path<ProductFormValues>),
      );
    },
  });

  const errors = form.formState.errors;
  const amountField = (name: keyof ProductFormValues, label: string, hint?: string) => (
    <FormField label={label} hint={hint} error={errors[name]?.message}>
      {(control) => <Input {...control} inputMode="decimal" autoComplete="off" {...form.register(name)} />}
    </FormField>
  );

  return (
    <Modal
      open
      onClose={onClose}
      busy={save.isPending}
      className="max-w-2xl"
      title={creating ? 'New deposit product' : `Edit draft version ${draft?.versionNo ?? ''}`}
      description={
        creating
          ? 'The product starts with a draft version. Publish it to start opening accounts.'
          : 'Accounts already open keep the terms of the version they were opened under.'
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="product-form" loading={save.isPending}>
            {creating ? 'Create product' : 'Save draft'}
          </Button>
        </>
      }
    >
      <form id="product-form" className="grid gap-4" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
        {save.isError ? <Alert tone="danger">{errorMessage(save.error)}</Alert> : null}
        {creating ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="Code" required error={errors.code?.message}>
              {(control) => <Input {...control} className="uppercase" maxLength={30} {...form.register('code')} />}
            </FormField>
            <FormField label="Type" required>
              {(control) => (
                <Select {...control} {...form.register('productType')}>
                  {PRODUCT_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {humanize(type)}
                    </option>
                  ))}
                </Select>
              )}
            </FormField>
            <FormField label="Name" required className="sm:col-span-2" error={errors.name?.message}>
              {(control) => <Input {...control} maxLength={120} {...form.register('name')} />}
            </FormField>
            <FormField label="Description" className="sm:col-span-2" error={errors.description?.message}>
              {(control) => <Textarea {...control} maxLength={500} {...form.register('description')} />}
            </FormField>
          </div>
        ) : null}
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label="Currency" required error={errors.currency?.message}>
            {(control) => <Input {...control} className="uppercase" maxLength={3} {...form.register('currency')} />}
          </FormField>
          <FormField label="Interest rate (% a year)" error={errors.interestRate?.message}>
            {(control) => <Input {...control} inputMode="decimal" {...form.register('interestRate')} />}
          </FormField>
          <FormField label="Required KYC tier" error={errors.requiredKycTier?.message}>
            {(control) => (
              <Select {...control} {...form.register('requiredKycTier')}>
                <option value="">Any verified customer</option>
                {(tiers.data ?? [])
                  .filter((tier) => tier.active)
                  .map((tier) => (
                    <option key={tier.code} value={tier.code}>
                      {tier.name} ({tier.code})
                    </option>
                  ))}
              </Select>
            )}
          </FormField>
          {amountField('minOpeningBalance', 'Opening deposit', 'Accounts stay pending until it is paid in')}
          {amountField('minOperatingBalance', 'Minimum balance')}
          {amountField('maxBalance', 'Maximum balance')}
          {amountField('maxWithdrawalAmount', 'Largest withdrawal')}
          {amountField('dailyWithdrawalLimit', 'Daily withdrawal limit', 'Withdrawals and transfers out per day')}
        </div>
        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="mb-2 text-sm font-medium">Charges</legend>
          {percentCharges.some((charge) => charge.event === 'CASH_WITHDRAWAL') ? null : amountField('withdrawalFee', 'Withdrawal fee (flat)')}
          {percentCharges.some((charge) => charge.event === 'TRANSFER_OUT') ? null : amountField('transferFee', 'Transfer fee (flat)')}
          {percentCharges.map((charge) => (
            <p key={charge.event} className="text-sm text-muted-foreground sm:col-span-2">
              {charge.name}: {charge.rate}% of the amount ({humanize(charge.event)}), kept as configured.
            </p>
          ))}
        </fieldset>
      </form>
    </Modal>
  );
}
