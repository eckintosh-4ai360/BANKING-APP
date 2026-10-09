'use client';

import { bff, Permission, type CustomerDetail } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import {
  Alert,
  Badge,
  Button,
  DetailList,
  FormField,
  Input,
  Modal,
  PageHeader,
  Select,
  Skeleton,
  StatusBadge,
  Tabs,
  Textarea,
  formatDate,
  formatDateTime,
  humanize,
} from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import type { z } from 'zod';
import { AccountsPanel } from '@/components/customer/accounts-panel';
import { DocumentsPanel } from '@/components/customer/documents-panel';
import { IdentificationsPanel } from '@/components/customer/identifications-panel';
import { KycPanel } from '@/components/customer/kyc-panel';
import { AddressesPanel, NextOfKinPanel, RelatedPartiesPanel } from '@/components/customer/records-panels';
import { customerKey, Section, useCanCapture, useCustomerRefresh } from '@/components/customer/shared';
import { useCan } from '@/lib/me';
import { useBranches } from '@/lib/queries';
import { contactSchema } from '@/lib/schemas';

export default function CustomerPage() {
  const { id } = useParams<{ id: string }>();
  const [tab, setTab] = useState('profile');
  const canViewAccounts = useCan(Permission.accountView);
  const customer = useQuery({
    queryKey: customerKey(id),
    queryFn: ({ signal }) => bff<CustomerDetail>(`/customers/${id}`, { signal }),
  });

  if (customer.isPending) {
    return (
      <div className="grid gap-4" aria-busy="true">
        <Skeleton className="h-10 w-80" />
        <Skeleton className="h-48" />
      </div>
    );
  }
  if (customer.isError) {
    return <Alert tone="danger">{errorMessage(customer.error)}</Alert>;
  }

  const data = customer.data;
  const tabs = [
    { id: 'profile', label: 'Profile' },
    { id: 'identity', label: 'Identity & addresses' },
    { id: 'relations', label: data.customerType === 'BUSINESS' ? 'Related parties' : 'Next of kin' },
    { id: 'documents', label: `Documents (${data.documents.length})` },
    { id: 'kyc', label: 'KYC' },
    ...(canViewAccounts ? [{ id: 'accounts', label: 'Accounts' }] : []),
  ];

  return (
    <>
      <PageHeader
        title={data.displayName}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <span className="font-mono">{data.customerNumber}</span>
            <StatusBadge status={data.status} />
            <Badge tone="neutral">KYC: {humanize(data.kycStatus)}</Badge>
            {data.kycTierCode ? <Badge tone="info">Tier {data.kycTierCode}</Badge> : null}
            <Badge tone={data.riskLevel === 'HIGH' ? 'danger' : 'neutral'}>Risk: {humanize(data.riskLevel)}</Badge>
          </span>
        }
        actions={<HeaderActions customer={data} />}
      />
      {data.statusReason ? (
        <Alert tone="warning" className="mb-4" title={`Status: ${humanize(data.status)}`}>
          {data.statusReason}
        </Alert>
      ) : null}
      <Tabs tabs={tabs} active={tab} onChange={setTab} />
      <div className="grid gap-6">
        {tab === 'profile' ? <ProfileSection customer={data} /> : null}
        {tab === 'identity' ? (
          <>
            <IdentificationsPanel customer={data} />
            <AddressesPanel customer={data} />
          </>
        ) : null}
        {tab === 'relations' ? (data.customerType === 'BUSINESS' ? <RelatedPartiesPanel customer={data} /> : <NextOfKinPanel customer={data} />) : null}
        {tab === 'documents' ? <DocumentsPanel customer={data} /> : null}
        {tab === 'kyc' ? <KycPanel customer={data} /> : null}
        {tab === 'accounts' && canViewAccounts ? <AccountsPanel customer={data} /> : null}
      </div>
    </>
  );
}

function ProfileSection({ customer }: { customer: CustomerDetail }) {
  const { nameOf } = useBranches();
  const general = [
    { label: 'Customer type', value: humanize(customer.customerType) },
    { label: 'Home branch', value: nameOf(customer.homeBranchId) },
    { label: 'Primary phone', value: customer.primaryPhone },
    { label: 'Email', value: customer.email },
    { label: 'Preferred language', value: customer.preferredLanguage },
    { label: 'Onboarding channel', value: humanize(customer.onboardingChannel) },
    { label: 'KYC verified', value: formatDateTime(customer.kycVerifiedAt) },
    { label: 'Customer since', value: formatDate(customer.createdAt) },
  ];
  const individual = customer.individual;
  const business = customer.business;
  return (
    <>
      <Section title="General">
        <DetailList items={general} columns={3} />
      </Section>
      {individual ? (
        <Section title="Personal details">
          <DetailList
            columns={3}
            items={[
              { label: 'Name', value: [individual.title, individual.firstName, individual.middleName, individual.lastName].filter(Boolean).join(' ') },
              { label: 'Date of birth', value: formatDate(individual.dateOfBirth) },
              { label: 'Gender', value: humanize(individual.gender) },
              { label: 'Nationality', value: individual.nationality },
              { label: 'Marital status', value: humanize(individual.maritalStatus) },
              { label: 'Employment', value: humanize(individual.employmentStatus) },
              { label: 'Occupation', value: individual.occupation },
              { label: 'Employer', value: individual.employerName },
              { label: 'Monthly income band', value: individual.monthlyIncomeBand },
              { label: 'Tax ID', value: individual.taxIdMasked ? <span className="font-mono">{individual.taxIdMasked}</span> : null },
            ]}
          />
        </Section>
      ) : null}
      {business ? (
        <Section title="Business details">
          <DetailList
            columns={3}
            items={[
              { label: 'Registered name', value: business.registeredName },
              { label: 'Trading name', value: business.tradingName },
              { label: 'Registration number', value: business.registrationNumber },
              { label: 'Registration date', value: formatDate(business.registrationDate) },
              { label: 'Business type', value: humanize(business.businessType) },
              { label: 'Industry', value: business.industrySector },
              { label: 'Annual turnover band', value: business.annualTurnoverBand },
              { label: 'Employees', value: business.numberOfEmployees?.toLocaleString() },
              { label: 'Tax ID', value: business.taxIdMasked ? <span className="font-mono">{business.taxIdMasked}</span> : null },
            ]}
          />
        </Section>
      ) : null}
    </>
  );
}

function HeaderActions({ customer }: { customer: CustomerDetail }) {
  const canCapture = useCanCapture(customer);
  const canFreeze = useCan(Permission.customerFreeze);
  const [editing, setEditing] = useState(false);
  const [changingStatus, setChangingStatus] = useState(false);
  return (
    <>
      {canCapture && customer.status !== 'CLOSED' ? (
        <Button variant="outline" onClick={() => setEditing(true)}>
          Edit contact details
        </Button>
      ) : null}
      {canFreeze && customer.status !== 'PENDING' && customer.status !== 'CLOSED' ? (
        <Button variant="outline" onClick={() => setChangingStatus(true)}>
          Change status
        </Button>
      ) : null}
      {editing ? <ContactDialog customer={customer} onClose={() => setEditing(false)} /> : null}
      {changingStatus ? <StatusDialog customer={customer} onClose={() => setChangingStatus(false)} /> : null}
    </>
  );
}

function ContactDialog({ customer, onClose }: { customer: CustomerDetail; onClose: () => void }) {
  const refresh = useCustomerRefresh(customer.id);
  const [failure, setFailure] = useState<string>();
  const form = useForm<z.input<typeof contactSchema>, unknown, z.output<typeof contactSchema>>({
    resolver: zodResolver(contactSchema),
    defaultValues: { primaryPhone: customer.primaryPhone ?? '', email: customer.email ?? '', preferredLanguage: customer.preferredLanguage ?? '' },
  });
  const save = useMutation({
    // The version makes the update fail cleanly if someone else changed the customer in the meantime.
    mutationFn: (values: z.output<typeof contactSchema>) =>
      bff(`/customers/${customer.id}`, { method: 'PUT', body: { ...values, relationshipOfficerId: customer.relationshipOfficerId, version: customer.version } }),
    onSuccess: () => {
      refresh();
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, ['primaryPhone', 'email', 'preferredLanguage'])) {
        setFailure(errorMessage(error));
      }
    },
  });
  const { errors } = form.formState;
  return (
    <Modal open onClose={onClose} busy={save.isPending} title="Edit contact details">
      <form className="grid gap-4" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
        {failure ? <Alert tone="danger">{failure}</Alert> : null}
        <FormField label="Primary phone" error={errors.primaryPhone?.message}>
          {(control) => <Input {...control} type="tel" {...form.register('primaryPhone')} />}
        </FormField>
        <FormField label="Email" error={errors.email?.message}>
          {(control) => <Input {...control} type="email" {...form.register('email')} />}
        </FormField>
        <FormField label="Preferred language" error={errors.preferredLanguage?.message}>
          {(control) => <Input {...control} {...form.register('preferredLanguage')} />}
        </FormField>
        <div className="flex justify-end gap-2">
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

function StatusDialog({ customer, onClose }: { customer: CustomerDetail; onClose: () => void }) {
  const refresh = useCustomerRefresh(customer.id);
  const options = ['ACTIVE', 'RESTRICTED', 'FROZEN', 'CLOSED'].filter((status) => status !== customer.status);
  const [status, setStatus] = useState(options[0] ?? '');
  const [reason, setReason] = useState('');
  const change = useMutation({
    mutationFn: () => bff(`/customers/${customer.id}/status`, { method: 'POST', body: { status, reason: reason.trim(), version: customer.version } }),
    onSuccess: () => {
      refresh();
      onClose();
    },
  });
  return (
    <Modal
      open
      onClose={onClose}
      busy={change.isPending}
      title="Change customer status"
      description="Restricting, freezing or closing a customer is audited and takes effect immediately."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={change.isPending}>
            Cancel
          </Button>
          <Button
            variant={status === 'ACTIVE' ? 'primary' : 'destructive'}
            loading={change.isPending}
            disabled={reason.trim().length < 3}
            onClick={() => change.mutate()}
          >
            Change to {humanize(status)}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {change.isError ? <Alert tone="danger">{errorMessage(change.error)}</Alert> : null}
        <FormField label="New status" required>
          {(control) => (
            <Select {...control} value={status} onChange={(event) => setStatus(event.target.value)}>
              {options.map((option) => (
                <option key={option} value={option}>
                  {humanize(option)}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="Reason" required hint="Recorded on the customer and in the audit log.">
          {(control) => <Textarea {...control} value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
