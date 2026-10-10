'use client';

import { bff, Permission, type ChannelSettings } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import { Alert, Badge, Button, Card, CardContent, CardFooter, CardHeader, CardTitle, Checkbox, DetailList, FormField, Input, Modal, Money, Select, Skeleton } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useCan } from '@/lib/me';
import { useBranches, useKycTiers, useProducts } from '@/lib/queries';
import { amountText } from '@/lib/schemas';

export const CHANNEL_SETTINGS_KEY = ['channel-settings'] as const;

/** Product types a new customer's first account can be. */
const FIRST_ACCOUNT_TYPES = new Set(['SAVINGS', 'CURRENT']);

/** The customer app: limits for transfers and sign-up for people who are not yet customers. */
export function CustomerAppSettings() {
  const settings = useQuery({ queryKey: CHANNEL_SETTINGS_KEY, queryFn: ({ signal }) => bff<ChannelSettings>('/channel-settings', { signal }) });
  const canManage = useCan(Permission.settingsManage);
  const [editing, setEditing] = useState<'limits' | 'sign-up' | null>(null);
  if (settings.isPending) {
    return <Skeleton className="h-64" />;
  }
  if (settings.isError) {
    return <Alert tone="danger">{errorMessage(settings.error)}</Alert>;
  }
  const value = settings.data;
  const money = (amount: string) => <Money amount={amount} currency={value.currency} />;
  const signUp = value.onboarding;
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>Transfer limits</CardTitle>
          <p className="text-sm text-muted-foreground">
            For transfers to other people from the app. Transfers between a customer&apos;s own accounts are not limited.
          </p>
        </CardHeader>
        <CardContent>
          <DetailList
            items={[
              { label: 'One transfer', value: money(value.maxTransferAmount) },
              { label: 'A day', value: money(value.dailyTransferLimit) },
              { label: 'After adding a beneficiary', value: <>{money(value.cooldownMaxAmount)} a transfer for {hours(value.beneficiaryCooldownHours)}</> },
              { label: 'After trusting a new device', value: <>{money(value.newDeviceMaxAmount)} a transfer for {hours(value.newDeviceCooldownHours)}</> },
            ]}
          />
        </CardContent>
        {canManage ? (
          <CardFooter>
            <Button size="sm" variant="outline" onClick={() => setEditing('limits')}>
              Edit limits
            </Button>
          </CardFooter>
        ) : null}
      </Card>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            Sign-up in the app {signUp.enabled ? <Badge tone="success">On</Badge> : <Badge tone="neutral">Off</Badge>}
          </CardTitle>
          <p className="text-sm text-muted-foreground">
            People who are not yet customers register in the app, capture their details and documents, and are reviewed in the KYC queue like any new customer. Once approved they open their first account.
          </p>
        </CardHeader>
        <CardContent>
          <DetailList
            items={[
              { label: 'Branch for new customers', value: signUp.branchName },
              { label: 'KYC tier', value: signUp.tierCode },
              { label: 'First account', value: signUp.productName },
              { label: 'Minimum age', value: `${signUp.minimumAge} years` },
            ]}
          />
        </CardContent>
        {canManage ? (
          <CardFooter>
            <Button size="sm" variant="outline" onClick={() => setEditing('sign-up')}>
              Edit sign-up
            </Button>
          </CardFooter>
        ) : null}
      </Card>
      {editing === 'limits' ? <LimitsDialog settings={value} onClose={() => setEditing(null)} /> : null}
      {editing === 'sign-up' ? <SignUpDialog settings={value} onClose={() => setEditing(null)} /> : null}
    </div>
  );
}

function hours(value: number) {
  return value === 1 ? '1 hour' : `${value} hours`;
}

// ----------------------------------------------------------------------------------------------- limits

const hoursText = z
  .string()
  .trim()
  .regex(/^[0-9]{1,3}$/, 'Enter whole hours')
  .refine((value) => Number(value) <= 720, 'At most 720 hours (30 days)');

const limitsSchema = z.object({
  maxTransferAmount: amountText,
  dailyTransferLimit: amountText,
  beneficiaryCooldownHours: hoursText,
  cooldownMaxAmount: amountText,
  newDeviceCooldownHours: hoursText,
  newDeviceMaxAmount: amountText,
});

const LIMIT_FIELDS = ['maxTransferAmount', 'dailyTransferLimit', 'beneficiaryCooldownHours', 'cooldownMaxAmount', 'newDeviceCooldownHours', 'newDeviceMaxAmount'] as const;

function LimitsDialog({ settings, onClose }: { settings: ChannelSettings; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string>();
  const form = useForm<z.input<typeof limitsSchema>, unknown, z.output<typeof limitsSchema>>({
    resolver: zodResolver(limitsSchema),
    defaultValues: {
      maxTransferAmount: settings.maxTransferAmount,
      dailyTransferLimit: settings.dailyTransferLimit,
      beneficiaryCooldownHours: String(settings.beneficiaryCooldownHours),
      cooldownMaxAmount: settings.cooldownMaxAmount,
      newDeviceCooldownHours: String(settings.newDeviceCooldownHours),
      newDeviceMaxAmount: settings.newDeviceMaxAmount,
    },
  });
  const save = useMutation({
    // Amounts stay strings end to end; the server checks them against each other and the currency.
    mutationFn: (values: z.output<typeof limitsSchema>) =>
      bff<ChannelSettings>('/channel-settings', {
        method: 'PUT',
        body: {
          ...values,
          beneficiaryCooldownHours: Number(values.beneficiaryCooldownHours),
          newDeviceCooldownHours: Number(values.newDeviceCooldownHours),
          version: settings.version,
        },
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData(CHANNEL_SETTINGS_KEY, saved);
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, LIMIT_FIELDS)) {
        setFailure(errorMessage(error));
      }
    },
  });
  const { errors } = form.formState;
  const field = (name: (typeof LIMIT_FIELDS)[number], label: string, hint?: string) => (
    <FormField label={label} error={errors[name]?.message} hint={hint} required>
      {(control) => <Input {...control} inputMode={name.endsWith('Hours') ? 'numeric' : 'decimal'} {...form.register(name)} />}
    </FormField>
  );
  return (
    <Modal open onClose={onClose} busy={save.isPending} title="Edit transfer limits" description={`In ${settings.currency}. The daily limit must cover one transfer; the limits after a new beneficiary or device may not exceed one transfer.`} className="max-w-2xl">
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
        {failure ? <Alert tone="danger" className="sm:col-span-2">{failure}</Alert> : null}
        {field('maxTransferAmount', 'One transfer')}
        {field('dailyTransferLimit', 'A day')}
        {field('cooldownMaxAmount', 'One transfer after adding a beneficiary')}
        {field('beneficiaryCooldownHours', 'For (hours)', 'Up to 720')}
        {field('newDeviceMaxAmount', 'One transfer after trusting a new device')}
        {field('newDeviceCooldownHours', 'For (hours)', 'Up to 720')}
        <div className="flex justify-end gap-2 sm:col-span-2">
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            Save
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ---------------------------------------------------------------------------------------------- sign-up

const signUpSchema = z
  .object({
    enabled: z.boolean(),
    branchId: z.string(),
    tierCode: z.string(),
    productId: z.string(),
    minimumAge: z
      .string()
      .trim()
      .regex(/^[0-9]{1,3}$/, 'Enter whole years')
      .refine((value) => Number(value) <= 120, 'At most 120'),
  })
  .superRefine((value, context) => {
    if (!value.enabled) {
      return;
    }
    if (!value.branchId) {
      context.addIssue({ code: 'custom', path: ['branchId'], message: 'Choose the branch new customers belong to' });
    }
    if (!value.tierCode) {
      context.addIssue({ code: 'custom', path: ['tierCode'], message: 'Choose the KYC tier' });
    }
    if (!value.productId) {
      context.addIssue({ code: 'custom', path: ['productId'], message: 'Choose the first account' });
    }
  });

function SignUpDialog({ settings, onClose }: { settings: ChannelSettings; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string>();
  const branches = useBranches();
  const tiers = useKycTiers();
  const products = useProducts();
  const current = settings.onboarding;
  const form = useForm<z.input<typeof signUpSchema>, unknown, z.output<typeof signUpSchema>>({
    resolver: zodResolver(signUpSchema),
    defaultValues: {
      enabled: current.enabled,
      branchId: current.branchId ?? '',
      tierCode: current.tierCode ?? '',
      productId: current.productId ?? '',
      minimumAge: String(current.minimumAge),
    },
  });
  const save = useMutation({
    mutationFn: (values: z.output<typeof signUpSchema>) =>
      bff<ChannelSettings>('/channel-settings/onboarding', {
        method: 'PUT',
        body: {
          enabled: values.enabled,
          branchId: values.branchId || null,
          tierCode: values.tierCode || null,
          productId: values.productId || null,
          minimumAge: Number(values.minimumAge),
          version: settings.version,
        },
      }),
    onSuccess: (saved) => {
      queryClient.setQueryData(CHANNEL_SETTINGS_KEY, saved);
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, ['branchId', 'tierCode', 'productId', 'minimumAge'])) {
        setFailure(errorMessage(error));
      }
    },
  });
  const { errors } = form.formState;
  // A first account must be a published savings or current product in the institution's currency.
  const firstAccounts = (products.data ?? []).filter(
    (product) => product.status === 'ACTIVE' && FIRST_ACCOUNT_TYPES.has(product.productType) && product.currentVersion?.currency === settings.currency,
  );
  const activeTiers = [...(tiers.data ?? [])].filter((tier) => tier.active).sort((a, b) => a.tierRank - b.tierRank);
  return (
    <Modal open onClose={onClose} busy={save.isPending} title="Sign-up in the app" description="Changes apply to sign-ups from now on; people already signing up keep their branch and tier." className="max-w-2xl">
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
        {failure ? <Alert tone="danger" className="sm:col-span-2">{failure}</Alert> : null}
        <Checkbox label="Let people sign up in the app" className="sm:col-span-2" {...form.register('enabled')} />
        <FormField label="Branch for new customers" error={errors.branchId?.message}>
          {(control) => (
            <Select {...control} {...form.register('branchId')}>
              <option value="">Choose a branch</option>
              {branches.activeBranches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name} ({branch.code})
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="KYC tier" error={errors.tierCode?.message} hint="What new customers must provide before approval">
          {(control) => (
            <Select {...control} {...form.register('tierCode')}>
              <option value="">Choose a tier</option>
              {activeTiers.map((tier) => (
                <option key={tier.code} value={tier.code}>
                  {tier.name} ({tier.code})
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="First account" error={errors.productId?.message} hint={`Savings or current, in ${settings.currency}`}>
          {(control) => (
            <Select {...control} {...form.register('productId')}>
              <option value="">Choose a product</option>
              {firstAccounts.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.name} ({product.code})
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="Minimum age" error={errors.minimumAge?.message} required>
          {(control) => <Input {...control} inputMode="numeric" {...form.register('minimumAge')} />}
        </FormField>
        <div className="flex justify-end gap-2 sm:col-span-2">
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            Save
          </Button>
        </div>
      </form>
    </Modal>
  );
}
