'use client';

import { bff, type CustomerDetail } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import { Alert, Button, Card, CardContent, CardHeader, CardTitle, FormField, Input, PageHeader, Select } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm, type FieldPath } from 'react-hook-form';
import { useMe } from '@/lib/me';
import { useBranches } from '@/lib/queries';
import { createCustomerSchema, type CreateCustomerInput, type CreateCustomerValues } from '@/lib/schemas';

const SERVER_FIELDS: FieldPath<CreateCustomerInput>[] = [
  'homeBranchId',
  'primaryPhone',
  'email',
  'individual.firstName',
  'individual.lastName',
  'individual.dateOfBirth',
  'individual.nationality',
  'individual.taxId',
  'business.registeredName',
  'business.registrationNumber',
  'business.businessType',
  'business.taxId',
];

const EMPTY_INDIVIDUAL = {
  title: '',
  firstName: '',
  middleName: '',
  lastName: '',
  dateOfBirth: '',
  gender: '',
  nationality: 'GH',
  maritalStatus: '',
  occupation: '',
  employerName: '',
  employmentStatus: '',
  monthlyIncomeBand: '',
  taxId: '',
} as const;

const EMPTY_BUSINESS = {
  registeredName: '',
  tradingName: '',
  registrationNumber: '',
  registrationDate: '',
  businessType: 'LIMITED_COMPANY',
  industrySector: '',
  annualTurnoverBand: '',
  numberOfEmployees: '',
  taxId: '',
} as const;

export default function NewCustomerPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const me = useMe();
  const { activeBranches, allowed: canListBranches } = useBranches();
  const [failure, setFailure] = useState<string>();

  const form = useForm<CreateCustomerInput, unknown, CreateCustomerValues>({
    resolver: zodResolver(createCustomerSchema),
    defaultValues: {
      customerType: 'INDIVIDUAL',
      homeBranchId: me.homeBranchId,
      onboardingChannel: 'BRANCH',
      primaryPhone: '',
      email: '',
      preferredLanguage: 'en',
      individual: { ...EMPTY_INDIVIDUAL },
      business: { ...EMPTY_BUSINESS },
    },
    shouldUnregister: true,
  });
  const customerType = form.watch('customerType');

  const create = useMutation({
    mutationFn: (values: CreateCustomerValues) => bff<CustomerDetail>('/customers', { method: 'POST', body: values }),
    onSuccess: (customer) => {
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
      router.push(`/customers/${customer.id}`);
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, SERVER_FIELDS)) {
        setFailure(errorMessage(error));
      }
    },
  });

  const errors = form.formState.errors;
  const individualErrors = errors.individual;
  const businessErrors = errors.business;

  return (
    <>
      <PageHeader title="New customer" description="Capture the customer's details. Identity documents and KYC follow on the customer record." />
      <form
        className="grid gap-6"
        noValidate
        onSubmit={form.handleSubmit((values) => {
          setFailure(undefined);
          create.mutate(values);
        })}
      >
        {failure ? <Alert tone="danger">{failure}</Alert> : null}
        <Card>
          <CardHeader>
            <CardTitle>Customer</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-3">
            <FormField label="Customer type" required>
              {(control) => (
                <Select {...control} {...form.register('customerType')}>
                  <option value="INDIVIDUAL">Individual</option>
                  <option value="BUSINESS">Business</option>
                </Select>
              )}
            </FormField>
            <FormField label="Home branch" error={errors.homeBranchId?.message} required>
              {(control) =>
                canListBranches ? (
                  <Select {...control} {...form.register('homeBranchId')}>
                    {activeBranches.map((branch) => (
                      <option key={branch.id} value={branch.id}>
                        {branch.name} ({branch.code})
                      </option>
                    ))}
                  </Select>
                ) : (
                  <Input {...control} value="Your home branch" readOnly disabled />
                )
              }
            </FormField>
            <FormField label="Onboarding channel" required>
              {(control) => (
                <Select {...control} {...form.register('onboardingChannel')}>
                  <option value="BRANCH">At a branch</option>
                  <option value="FIELD">In the field</option>
                </Select>
              )}
            </FormField>
            <FormField label="Primary phone" error={errors.primaryPhone?.message} hint="e.g. +233241234567">
              {(control) => <Input {...control} type="tel" autoComplete="off" {...form.register('primaryPhone')} />}
            </FormField>
            <FormField label="Email" error={errors.email?.message}>
              {(control) => <Input {...control} type="email" autoComplete="off" {...form.register('email')} />}
            </FormField>
            <FormField label="Preferred language" error={errors.preferredLanguage?.message} hint="e.g. en, tw, ee">
              {(control) => <Input {...control} {...form.register('preferredLanguage')} />}
            </FormField>
          </CardContent>
        </Card>

        {customerType === 'INDIVIDUAL' ? (
          <Card>
            <CardHeader>
              <CardTitle>Personal details</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-3">
              <FormField label="Title" error={individualErrors?.title?.message}>
                {(control) => <Input {...control} placeholder="Mr, Mrs, Dr…" {...form.register('individual.title')} />}
              </FormField>
              <FormField label="First name" error={individualErrors?.firstName?.message} required>
                {(control) => <Input {...control} autoComplete="off" {...form.register('individual.firstName')} />}
              </FormField>
              <FormField label="Middle name" error={individualErrors?.middleName?.message}>
                {(control) => <Input {...control} autoComplete="off" {...form.register('individual.middleName')} />}
              </FormField>
              <FormField label="Last name" error={individualErrors?.lastName?.message} required>
                {(control) => <Input {...control} autoComplete="off" {...form.register('individual.lastName')} />}
              </FormField>
              <FormField label="Date of birth" error={individualErrors?.dateOfBirth?.message} required>
                {(control) => <Input {...control} type="date" {...form.register('individual.dateOfBirth')} />}
              </FormField>
              <FormField label="Gender">
                {(control) => (
                  <Select {...control} {...form.register('individual.gender')}>
                    <option value="">Not recorded</option>
                    <option value="FEMALE">Female</option>
                    <option value="MALE">Male</option>
                    <option value="OTHER">Other</option>
                    <option value="UNDISCLOSED">Prefers not to say</option>
                  </Select>
                )}
              </FormField>
              <FormField label="Nationality" error={individualErrors?.nationality?.message} hint="2-letter code">
                {(control) => <Input {...control} maxLength={2} className="uppercase" {...form.register('individual.nationality', { setValueAs: (value: string) => value.toUpperCase() })} />}
              </FormField>
              <FormField label="Marital status">
                {(control) => (
                  <Select {...control} {...form.register('individual.maritalStatus')}>
                    <option value="">Not recorded</option>
                    <option value="SINGLE">Single</option>
                    <option value="MARRIED">Married</option>
                    <option value="DIVORCED">Divorced</option>
                    <option value="WIDOWED">Widowed</option>
                    <option value="SEPARATED">Separated</option>
                    <option value="UNDISCLOSED">Prefers not to say</option>
                  </Select>
                )}
              </FormField>
              <FormField label="Employment status">
                {(control) => (
                  <Select {...control} {...form.register('individual.employmentStatus')}>
                    <option value="">Not recorded</option>
                    <option value="EMPLOYED">Employed</option>
                    <option value="SELF_EMPLOYED">Self-employed</option>
                    <option value="UNEMPLOYED">Unemployed</option>
                    <option value="STUDENT">Student</option>
                    <option value="RETIRED">Retired</option>
                    <option value="OTHER">Other</option>
                  </Select>
                )}
              </FormField>
              <FormField label="Occupation" error={individualErrors?.occupation?.message}>
                {(control) => <Input {...control} {...form.register('individual.occupation')} />}
              </FormField>
              <FormField label="Employer" error={individualErrors?.employerName?.message}>
                {(control) => <Input {...control} {...form.register('individual.employerName')} />}
              </FormField>
              <FormField label="Monthly income band" error={individualErrors?.monthlyIncomeBand?.message}>
                {(control) => <Input {...control} placeholder="e.g. 1000-2999" {...form.register('individual.monthlyIncomeBand')} />}
              </FormField>
              <FormField label="Tax identification number" error={individualErrors?.taxId?.message} hint="Stored encrypted; shown masked afterwards">
                {(control) => <Input {...control} autoComplete="off" {...form.register('individual.taxId')} />}
              </FormField>
            </CardContent>
          </Card>
        ) : (
          <Card>
            <CardHeader>
              <CardTitle>Business details</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-4 sm:grid-cols-3">
              <FormField label="Registered name" error={businessErrors?.registeredName?.message} required className="sm:col-span-2">
                {(control) => <Input {...control} {...form.register('business.registeredName')} />}
              </FormField>
              <FormField label="Trading name" error={businessErrors?.tradingName?.message}>
                {(control) => <Input {...control} {...form.register('business.tradingName')} />}
              </FormField>
              <FormField label="Registration number" error={businessErrors?.registrationNumber?.message} required>
                {(control) => <Input {...control} {...form.register('business.registrationNumber')} />}
              </FormField>
              <FormField label="Registration date" error={businessErrors?.registrationDate?.message}>
                {(control) => <Input {...control} type="date" {...form.register('business.registrationDate')} />}
              </FormField>
              <FormField label="Business type" error={businessErrors?.businessType?.message} required>
                {(control) => (
                  <Select {...control} {...form.register('business.businessType')}>
                    <option value="SOLE_PROPRIETORSHIP">Sole proprietorship</option>
                    <option value="PARTNERSHIP">Partnership</option>
                    <option value="LIMITED_COMPANY">Limited company</option>
                    <option value="COOPERATIVE">Cooperative</option>
                    <option value="NGO">NGO</option>
                    <option value="ASSOCIATION">Association</option>
                    <option value="OTHER">Other</option>
                  </Select>
                )}
              </FormField>
              <FormField label="Industry sector" error={businessErrors?.industrySector?.message}>
                {(control) => <Input {...control} {...form.register('business.industrySector')} />}
              </FormField>
              <FormField label="Annual turnover band" error={businessErrors?.annualTurnoverBand?.message}>
                {(control) => <Input {...control} {...form.register('business.annualTurnoverBand')} />}
              </FormField>
              <FormField label="Number of employees" error={businessErrors?.numberOfEmployees?.message}>
                {(control) => <Input {...control} inputMode="numeric" {...form.register('business.numberOfEmployees')} />}
              </FormField>
              <FormField label="Tax identification number" error={businessErrors?.taxId?.message} hint="Stored encrypted; shown masked afterwards">
                {(control) => <Input {...control} autoComplete="off" {...form.register('business.taxId')} />}
              </FormField>
            </CardContent>
          </Card>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => router.back()}>
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending}>
            Create customer
          </Button>
        </div>
      </form>
    </>
  );
}
