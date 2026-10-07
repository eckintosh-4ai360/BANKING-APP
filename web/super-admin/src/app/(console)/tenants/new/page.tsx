'use client';

import { bff, Permission, type OnboardingResult } from '@banking/api';
import { applyFieldErrors, errorMessage, OneTimeCredentialDialog } from '@banking/console';
import { Alert, Button, Card, CardContent, CardHeader, CardTitle, Checkbox, FormField, Input, PageHeader, Select, humanize } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { useCan } from '@/lib/me';
import { GHANA_DEFAULTS, INSTITUTION_TYPES, onboardingSchema, type OnboardingInput, type OnboardingValues } from '@/lib/onboarding';

interface Feature {
  code: string;
  name: string;
  description: string;
}

const SERVER_FIELDS: FieldPath<OnboardingInput>[] = [
  'code',
  'legalName',
  'displayName',
  'institutionType',
  'countryCode',
  'baseCurrency',
  'timezone',
  'locale',
  'licenceNumber',
  'contactEmail',
  'contactPhone',
  'headOffice.code',
  'headOffice.name',
  'headOffice.digitalAddress',
  'administrator.firstName',
  'administrator.lastName',
  'administrator.email',
  'administrator.phone',
  'administrator.username',
];

export default function OnboardTenantPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const canManage = useCan(Permission.platformTenantManage);
  const [failure, setFailure] = useState<string>();
  const [result, setResult] = useState<OnboardingResult | null>(null);
  const features = useQuery({ queryKey: ['platform-features'], queryFn: ({ signal }) => bff<Feature[]>('/platform/features', { signal }) });

  const form = useForm<OnboardingInput, unknown, OnboardingValues>({
    resolver: zodResolver(onboardingSchema),
    defaultValues: {
      code: '',
      legalName: '',
      displayName: '',
      institutionType: 'MICROFINANCE',
      ...GHANA_DEFAULTS,
      licenceNumber: '',
      contactEmail: '',
      contactPhone: '',
      headOffice: { code: 'HQ', name: 'Head Office', city: '', region: '', digitalAddress: '' },
      administrator: { firstName: '', lastName: '', email: '', phone: '', username: '' },
      features: [],
    },
  });

  const onboard = useMutation({
    mutationFn: (values: OnboardingValues) => bff<OnboardingResult>('/platform/tenants', { method: 'POST', body: values }),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ['tenants'] });
      setResult(created);
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, SERVER_FIELDS)) {
        setFailure(errorMessage(error));
      }
    },
  });

  if (!canManage) {
    return <Alert tone="info">You don't have permission to onboard institutions.</Alert>;
  }

  const { errors } = form.formState;
  return (
    <>
      <PageHeader title="Onboard institution" description="Creates the institution, its head office, default roles and KYC settings, and its first administrator." />
      <form
        className="grid gap-6"
        noValidate
        onSubmit={form.handleSubmit((values) => {
          setFailure(undefined);
          onboard.mutate(values);
        })}
      >
        {failure ? <Alert tone="danger">{failure}</Alert> : null}
        <Card>
          <CardHeader>
            <CardTitle>Institution</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-3">
            <FormField label="Institution code" error={errors.code?.message} hint="Used at sign-in, e.g. akwaaba-mfi" required>
              {(control) => <Input {...control} autoCapitalize="none" {...form.register('code')} />}
            </FormField>
            <FormField label="Legal name" error={errors.legalName?.message} required className="sm:col-span-2">
              {(control) => <Input {...control} {...form.register('legalName')} />}
            </FormField>
            <FormField label="Display name" error={errors.displayName?.message} required>
              {(control) => <Input {...control} {...form.register('displayName')} />}
            </FormField>
            <FormField label="Institution type" required>
              {(control) => (
                <Select {...control} {...form.register('institutionType')}>
                  {INSTITUTION_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {humanize(type)}
                    </option>
                  ))}
                </Select>
              )}
            </FormField>
            <FormField label="Licence number" error={errors.licenceNumber?.message}>
              {(control) => <Input {...control} {...form.register('licenceNumber')} />}
            </FormField>
            <FormField label="Country" error={errors.countryCode?.message} required>
              {(control) => <Input {...control} maxLength={2} {...form.register('countryCode')} />}
            </FormField>
            <FormField label="Base currency" error={errors.baseCurrency?.message} required>
              {(control) => <Input {...control} maxLength={3} {...form.register('baseCurrency')} />}
            </FormField>
            <FormField label="Time zone" error={errors.timezone?.message} required>
              {(control) => <Input {...control} {...form.register('timezone')} />}
            </FormField>
            <FormField label="Locale" error={errors.locale?.message} required>
              {(control) => <Input {...control} {...form.register('locale')} />}
            </FormField>
            <FormField label="Contact email" error={errors.contactEmail?.message} required>
              {(control) => <Input {...control} type="email" {...form.register('contactEmail')} />}
            </FormField>
            <FormField label="Contact phone" error={errors.contactPhone?.message}>
              {(control) => <Input {...control} type="tel" {...form.register('contactPhone')} />}
            </FormField>
          </CardContent>
        </Card>

        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Head office</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <FormField label="Branch code" error={errors.headOffice?.code?.message} required>
                {(control) => <Input {...control} {...form.register('headOffice.code')} />}
              </FormField>
              <FormField label="Name" error={errors.headOffice?.name?.message} required>
                {(control) => <Input {...control} {...form.register('headOffice.name')} />}
              </FormField>
              <FormField label="City / town" error={errors.headOffice?.city?.message}>
                {(control) => <Input {...control} {...form.register('headOffice.city')} />}
              </FormField>
              <FormField label="Region" error={errors.headOffice?.region?.message}>
                {(control) => <Input {...control} {...form.register('headOffice.region')} />}
              </FormField>
              <FormField label="Digital address" error={errors.headOffice?.digitalAddress?.message}>
                {(control) => <Input {...control} {...form.register('headOffice.digitalAddress')} />}
              </FormField>
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>First administrator</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-2">
              <FormField label="First name" error={errors.administrator?.firstName?.message} required>
                {(control) => <Input {...control} {...form.register('administrator.firstName')} />}
              </FormField>
              <FormField label="Last name" error={errors.administrator?.lastName?.message} required>
                {(control) => <Input {...control} {...form.register('administrator.lastName')} />}
              </FormField>
              <FormField label="Email" error={errors.administrator?.email?.message} required>
                {(control) => <Input {...control} type="email" {...form.register('administrator.email')} />}
              </FormField>
              <FormField label="Phone" error={errors.administrator?.phone?.message}>
                {(control) => <Input {...control} type="tel" {...form.register('administrator.phone')} />}
              </FormField>
              <FormField label="Username" error={errors.administrator?.username?.message} required>
                {(control) => <Input {...control} autoCapitalize="none" {...form.register('administrator.username')} />}
              </FormField>
              <p className="text-xs text-muted-foreground sm:col-span-2">
                The administrator manages staff, roles and settings. They don't receive permissions to move money; the institution assigns those itself.
              </p>
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Licensed features</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {features.isError ? <Alert tone="danger">{errorMessage(features.error)}</Alert> : null}
            {(features.data ?? []).map((feature) => (
              <Checkbox
                key={feature.code}
                value={feature.code}
                label={
                  <span className="grid">
                    <span>{feature.name}</span>
                    <span className="text-xs text-muted-foreground">{feature.description}</span>
                  </span>
                }
                {...form.register('features')}
              />
            ))}
          </CardContent>
        </Card>

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => router.back()}>
            Cancel
          </Button>
          <Button type="submit" loading={onboard.isPending}>
            Onboard institution
          </Button>
        </div>
      </form>

      <OneTimeCredentialDialog
        title={result ? `${result.tenant.displayName} is ready` : ''}
        credential={result?.administratorCredential ?? null}
        onClose={() => {
          const id = result?.tenant.id;
          setResult(null);
          if (id) {
            router.push(`/tenants/${id}`);
          }
        }}
      />
    </>
  );
}
