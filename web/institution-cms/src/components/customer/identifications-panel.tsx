'use client';

import { bff, Permission, type CustomerDetail, type Identification } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import { Alert, Badge, Button, Checkbox, EmptyState, FormField, Input, Modal, Select, StatusBadge, formatDate } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Eye, Plus } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useCan } from '@/lib/me';
import { useIdentificationTypes } from '@/lib/queries';
import { COUNTRY, optionalDate } from '@/lib/schemas';
import { ConfirmAction, Section, useCanCapture, useCustomerRefresh } from './shared';

const schema = z.object({
  idTypeCode: z.string().min(1, 'Choose the document type'),
  idNumber: z.string().trim().min(1, 'Enter the number').max(50),
  issuingCountry: z
    .string()
    .trim()
    .toUpperCase()
    .refine((value) => value === '' || COUNTRY.test(value), 'Use a 2-letter country code')
    .transform((value) => value || undefined),
  issueDate: optionalDate,
  expiryDate: optionalDate,
  primary: z.boolean(),
});

type Values = z.output<typeof schema>;

export function IdentificationsPanel({ customer }: { customer: CustomerDetail }) {
  const canCapture = useCanCapture(customer);
  const canReveal = useCan(Permission.kycReview);
  const refresh = useCustomerRefresh(customer.id);
  const [adding, setAdding] = useState(false);
  const active = customer.identifications.filter((identification) => identification.active);

  return (
    <Section
      title="Identification"
      action={
        canCapture ? (
          <Button size="sm" variant="outline" onClick={() => setAdding(true)}>
            <Plus aria-hidden="true" />
            Add
          </Button>
        ) : null
      }
    >
      {customer.kycStatus === 'VERIFIED' ? (
        <Alert tone="info" className="mb-4">
          Identity data is locked while the customer is verified. Open an update KYC case to change it.
        </Alert>
      ) : null}
      {active.length === 0 ? (
        <EmptyState title="No identification recorded" description="Add the customer's national ID (e.g. Ghana Card) or passport." />
      ) : (
        <ul className="divide-y">
          {active.map((identification) => (
            <li key={identification.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="grid gap-0.5">
                <p className="flex items-center gap-2 font-medium">
                  {identification.idTypeCode.replaceAll('_', ' ')}
                  {identification.primary ? <Badge tone="info">Primary</Badge> : null}
                  <StatusBadge status={identification.verificationStatus} />
                </p>
                <p className="font-mono text-sm">{identification.idNumberMasked}</p>
                <p className="text-xs text-muted-foreground">
                  {identification.issuingCountry ?? '—'} · issued {formatDate(identification.issueDate)} · expires {formatDate(identification.expiryDate)}
                  {identification.verificationReference ? ` · ref ${identification.verificationReference}` : ''}
                </p>
              </div>
              <div className="flex gap-2">
                {canReveal ? <RevealNumber customerId={customer.id} identification={identification} /> : null}
                {canCapture ? (
                  <ConfirmAction
                    label="Remove"
                    variant="ghost"
                    destructive
                    title="Remove identification?"
                    description="The record is deactivated, not deleted, and the change is audited."
                    confirmLabel="Remove"
                    onConfirm={() => bff(`/customers/${customer.id}/identifications/${identification.id}`, { method: 'DELETE' })}
                    onDone={refresh}
                  />
                ) : null}
              </div>
            </li>
          ))}
        </ul>
      )}
      <AddIdentification open={adding} customer={customer} onClose={() => setAdding(false)} onAdded={refresh} />
    </Section>
  );
}

function AddIdentification({ open, customer, onClose, onAdded }: { open: boolean; customer: CustomerDetail; onClose: () => void; onAdded: () => void }) {
  const types = useIdentificationTypes();
  const [failure, setFailure] = useState<string>();
  const applicable = (types.data ?? []).filter(
    (type) => type.active && (type.appliesTo === 'ANY' || type.appliesTo === customer.customerType),
  );
  const form = useForm<z.input<typeof schema>, unknown, Values>({
    resolver: zodResolver(schema),
    defaultValues: { idTypeCode: '', idNumber: '', issuingCountry: 'GH', issueDate: '', expiryDate: '', primary: customer.identifications.length === 0 },
  });
  const selected = applicable.find((type) => type.code === form.watch('idTypeCode'));

  const add = useMutation({
    mutationFn: (values: Values) => bff(`/customers/${customer.id}/identifications`, { method: 'POST', body: values }),
    onSuccess: () => {
      form.reset();
      onAdded();
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, ['idTypeCode', 'idNumber', 'issuingCountry', 'issueDate', 'expiryDate'])) {
        setFailure(errorMessage(error));
      }
    },
  });

  function submit(values: Values) {
    setFailure(undefined);
    if (selected?.requiresExpiry && !values.expiryDate) {
      form.setError('expiryDate', { message: 'This document type needs an expiry date' });
      return;
    }
    if (selected?.formatRegex && !new RegExp(selected.formatRegex).test(values.idNumber)) {
      form.setError('idNumber', { message: selected.formatHint ? `Expected format: ${selected.formatHint}` : 'The number has the wrong format' });
      return;
    }
    add.mutate(values);
  }

  const { errors } = form.formState;
  return (
    <Modal open={open} onClose={onClose} busy={add.isPending} title="Add identification" description="The number is encrypted at rest and shown masked.">
      <form className="grid gap-4" noValidate onSubmit={form.handleSubmit(submit)}>
        {failure ? <Alert tone="danger">{failure}</Alert> : null}
        <FormField label="Document type" error={errors.idTypeCode?.message} required>
          {(control) => (
            <Select {...control} {...form.register('idTypeCode')}>
              <option value="">Choose…</option>
              {applicable.map((type) => (
                <option key={type.code} value={type.code}>
                  {type.name}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="Number" error={errors.idNumber?.message} hint={selected?.formatHint ?? undefined} required>
          {(control) => <Input {...control} autoComplete="off" spellCheck={false} {...form.register('idNumber')} />}
        </FormField>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label="Issuing country" error={errors.issuingCountry?.message}>
            {(control) => <Input {...control} maxLength={2} {...form.register('issuingCountry')} />}
          </FormField>
          <FormField label="Issue date" error={errors.issueDate?.message}>
            {(control) => <Input {...control} type="date" {...form.register('issueDate')} />}
          </FormField>
          <FormField label="Expiry date" error={errors.expiryDate?.message} required={selected?.requiresExpiry}>
            {(control) => <Input {...control} type="date" {...form.register('expiryDate')} />}
          </FormField>
        </div>
        <Checkbox label="Primary identification" {...form.register('primary')} />
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={add.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={add.isPending}>
            Add identification
          </Button>
        </div>
      </form>
    </Modal>
  );
}

/**
 * Reveals the full number on request. Each reveal is a separately audited backend call (kyc.review); the value is
 * kept only in component state and hidden again after 30 seconds.
 */
function RevealNumber({ customerId, identification }: { customerId: string; identification: Identification }) {
  const [value, setValue] = useState<string>();
  const reveal = useMutation({
    mutationFn: () => bff<{ idNumber: string }>(`/customers/${customerId}/identifications/${identification.id}/number`),
    onSuccess: (revealed) => setValue(revealed.idNumber),
  });

  useEffect(() => {
    if (!value) {
      return;
    }
    const timer = window.setTimeout(() => setValue(undefined), 30_000);
    return () => window.clearTimeout(timer);
  }, [value]);

  if (value) {
    return (
      <span className="flex items-center gap-2">
        <code className="rounded bg-warning-soft px-2 py-1 font-mono text-sm">{value}</code>
        <Button size="sm" variant="ghost" onClick={() => setValue(undefined)}>
          Hide
        </Button>
      </span>
    );
  }
  return (
    <span className="flex items-center gap-2">
      {reveal.isError ? <span className="text-xs text-destructive">{errorMessage(reveal.error)}</span> : null}
      <Button size="sm" variant="ghost" loading={reveal.isPending} onClick={() => reveal.mutate()} title="Reveals the full number; this is audited">
        <Eye aria-hidden="true" />
        Reveal
      </Button>
    </span>
  );
}
