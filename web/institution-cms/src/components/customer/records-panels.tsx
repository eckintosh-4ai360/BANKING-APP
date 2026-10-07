'use client';

import { bff, type CustomerDetail } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import { Alert, Badge, Button, Checkbox, EmptyState, FormField, Input, Modal, Select, formatDateTime, humanize } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useForm, type FieldValues, type Path, type UseFormReturn } from 'react-hook-form';
import { z } from 'zod';
import { COUNTRY, DIGITAL_ADDRESS, isPastDate, optionalEmail, optionalPattern, optionalPhone, optionalText, requiredText } from '@/lib/schemas';
import { ConfirmAction, Section, useCanCapture, useCustomerRefresh } from './shared';

// ------------------------------------------------------------------------------------------------- addresses

const addressSchema = z.object({
  addressType: z.enum(['RESIDENTIAL', 'BUSINESS', 'MAILING', 'PERMANENT']),
  line1: requiredText(200, 'Enter the address'),
  line2: optionalText(200),
  city: optionalText(100),
  district: optionalText(100),
  region: optionalText(100),
  countryCode: z.string().trim().toUpperCase().regex(COUNTRY, 'Use a 2-letter country code'),
  digitalAddress: optionalPattern(DIGITAL_ADDRESS, 'e.g. GA-123-4567'),
  landmark: optionalText(200),
  primary: z.boolean(),
});

export function AddressesPanel({ customer }: { customer: CustomerDetail }) {
  const canCapture = useCanCapture(customer);
  const refresh = useCustomerRefresh(customer.id);
  const [adding, setAdding] = useState(false);
  const addresses = customer.addresses.filter((address) => address.active);
  const form = useForm<z.input<typeof addressSchema>, unknown, z.output<typeof addressSchema>>({
    resolver: zodResolver(addressSchema),
    defaultValues: {
      addressType: customer.customerType === 'BUSINESS' ? 'BUSINESS' : 'RESIDENTIAL',
      line1: '',
      line2: '',
      city: '',
      district: '',
      region: '',
      countryCode: 'GH',
      digitalAddress: '',
      landmark: '',
      primary: addresses.length === 0,
    },
  });

  return (
    <Section title="Addresses" action={canCapture ? <AddButton onClick={() => setAdding(true)} /> : null}>
      {addresses.length === 0 ? (
        <EmptyState title="No address recorded" />
      ) : (
        <ul className="divide-y">
          {addresses.map((address) => (
            <li key={address.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
              <div className="grid gap-0.5 text-sm">
                <p className="flex items-center gap-2 font-medium">
                  {humanize(address.addressType)}
                  {address.primary ? <Badge tone="info">Primary</Badge> : null}
                  {address.verifiedAt ? <Badge tone="success">Verified {formatDateTime(address.verifiedAt)}</Badge> : null}
                </p>
                <p>{[address.line1, address.line2].filter(Boolean).join(', ')}</p>
                <p className="text-muted-foreground">
                  {[address.city, address.district, address.region, address.countryCode].filter(Boolean).join(', ')}
                  {address.digitalAddress ? ` · ${address.digitalAddress}` : ''}
                </p>
                {address.landmark ? <p className="text-xs text-muted-foreground">Landmark: {address.landmark}</p> : null}
              </div>
              {canCapture ? (
                <ConfirmAction
                  label="Remove"
                  variant="ghost"
                  destructive
                  title="Remove address?"
                  description="The address is deactivated and the change is audited."
                  confirmLabel="Remove"
                  onConfirm={() => bff(`/customers/${customer.id}/addresses/${address.id}`, { method: 'DELETE' })}
                  onDone={refresh}
                />
              ) : null}
            </li>
          ))}
        </ul>
      )}
      <RecordForm
        open={adding}
        title="Add address"
        onClose={() => setAdding(false)}
        form={form}
        fields={['line1', 'line2', 'city', 'district', 'region', 'countryCode', 'digitalAddress', 'landmark']}
        submit={(values) => bff(`/customers/${customer.id}/addresses`, { method: 'POST', body: values })}
        onSaved={refresh}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Type" required>
            {(control) => (
              <Select {...control} {...form.register('addressType')}>
                <option value="RESIDENTIAL">Residential</option>
                <option value="BUSINESS">Business</option>
                <option value="MAILING">Mailing</option>
                <option value="PERMANENT">Permanent</option>
              </Select>
            )}
          </FormField>
          <FormField label="Digital address (GhanaPost GPS)" error={form.formState.errors.digitalAddress?.message}>
            {(control) => <Input {...control} placeholder="GA-123-4567" {...form.register('digitalAddress')} />}
          </FormField>
          <FormField label="Address line 1" error={form.formState.errors.line1?.message} required className="sm:col-span-2">
            {(control) => <Input {...control} {...form.register('line1')} />}
          </FormField>
          <FormField label="Address line 2" error={form.formState.errors.line2?.message} className="sm:col-span-2">
            {(control) => <Input {...control} {...form.register('line2')} />}
          </FormField>
          <FormField label="City / town" error={form.formState.errors.city?.message}>
            {(control) => <Input {...control} {...form.register('city')} />}
          </FormField>
          <FormField label="District" error={form.formState.errors.district?.message}>
            {(control) => <Input {...control} {...form.register('district')} />}
          </FormField>
          <FormField label="Region" error={form.formState.errors.region?.message}>
            {(control) => <Input {...control} {...form.register('region')} />}
          </FormField>
          <FormField label="Country" error={form.formState.errors.countryCode?.message} required>
            {(control) => <Input {...control} maxLength={2} {...form.register('countryCode')} />}
          </FormField>
          <FormField label="Landmark" error={form.formState.errors.landmark?.message} className="sm:col-span-2">
            {(control) => <Input {...control} {...form.register('landmark')} />}
          </FormField>
          <Checkbox label="Primary address" {...form.register('primary')} />
        </div>
      </RecordForm>
    </Section>
  );
}

// ---------------------------------------------------------------------------------------------- next of kin

const kinSchema = z.object({
  fullName: requiredText(200, 'Enter the full name'),
  relationship: requiredText(50, 'Enter the relationship'),
  phone: optionalPhone,
  email: optionalEmail,
  address: optionalText(300),
  primary: z.boolean(),
});

export function NextOfKinPanel({ customer }: { customer: CustomerDetail }) {
  const canCapture = useCanCapture(customer);
  const refresh = useCustomerRefresh(customer.id);
  const [adding, setAdding] = useState(false);
  const kin = customer.nextOfKin.filter((person) => person.active);
  const form = useForm<z.input<typeof kinSchema>, unknown, z.output<typeof kinSchema>>({
    resolver: zodResolver(kinSchema),
    defaultValues: { fullName: '', relationship: '', phone: '', email: '', address: '', primary: kin.length === 0 },
  });

  return (
    <Section title="Next of kin" action={canCapture ? <AddButton onClick={() => setAdding(true)} /> : null}>
      {kin.length === 0 ? (
        <EmptyState title="No next of kin recorded" />
      ) : (
        <ul className="divide-y">
          {kin.map((person) => (
            <li key={person.id} className="flex flex-wrap items-start justify-between gap-3 py-3 text-sm">
              <div className="grid gap-0.5">
                <p className="flex items-center gap-2 font-medium">
                  {person.fullName}
                  {person.primary ? <Badge tone="info">Primary</Badge> : null}
                </p>
                <p className="text-muted-foreground">
                  {person.relationship}
                  {person.phone ? ` · ${person.phone}` : ''}
                  {person.email ? ` · ${person.email}` : ''}
                </p>
                {person.address ? <p className="text-xs text-muted-foreground">{person.address}</p> : null}
              </div>
              {canCapture ? (
                <ConfirmAction
                  label="Remove"
                  variant="ghost"
                  destructive
                  title="Remove next of kin?"
                  description="The record is deactivated and the change is audited."
                  confirmLabel="Remove"
                  onConfirm={() => bff(`/customers/${customer.id}/next-of-kin/${person.id}`, { method: 'DELETE' })}
                  onDone={refresh}
                />
              ) : null}
            </li>
          ))}
        </ul>
      )}
      <RecordForm
        open={adding}
        title="Add next of kin"
        onClose={() => setAdding(false)}
        form={form}
        fields={['fullName', 'relationship', 'phone', 'email', 'address']}
        submit={(values) => bff(`/customers/${customer.id}/next-of-kin`, { method: 'POST', body: values })}
        onSaved={refresh}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Full name" error={form.formState.errors.fullName?.message} required>
            {(control) => <Input {...control} {...form.register('fullName')} />}
          </FormField>
          <FormField label="Relationship" error={form.formState.errors.relationship?.message} required>
            {(control) => <Input {...control} placeholder="e.g. Sister" {...form.register('relationship')} />}
          </FormField>
          <FormField label="Phone" error={form.formState.errors.phone?.message}>
            {(control) => <Input {...control} type="tel" {...form.register('phone')} />}
          </FormField>
          <FormField label="Email" error={form.formState.errors.email?.message}>
            {(control) => <Input {...control} type="email" {...form.register('email')} />}
          </FormField>
          <FormField label="Address" error={form.formState.errors.address?.message} className="sm:col-span-2">
            {(control) => <Input {...control} {...form.register('address')} />}
          </FormField>
          <Checkbox label="Primary next of kin" {...form.register('primary')} />
        </div>
      </RecordForm>
    </Section>
  );
}

// ------------------------------------------------------------------------------------------- related parties

const partySchema = z.object({
  fullName: requiredText(200, 'Enter the full name'),
  role: z.enum(['DIRECTOR', 'SHAREHOLDER', 'BENEFICIAL_OWNER', 'AUTHORISED_SIGNATORY', 'PARTNER', 'TRUSTEE']),
  ownershipPercent: z
    .string()
    .trim()
    .refine((value) => value === '' || /^\d{1,3}(\.\d{1,2})?$/.test(value) && Number.parseFloat(value) <= 100, 'Enter a percentage between 0 and 100')
    // Sent as a decimal string; the backend parses it into BigDecimal without floating-point conversion.
    .transform((value) => (value === '' ? undefined : value)),
  nationality: optionalPattern(COUNTRY, 'Use a 2-letter country code'),
  dateOfBirth: z
    .string()
    .refine((value) => value === '' || isPastDate(value), 'The date of birth must be in the past')
    .transform((value) => (value === '' ? undefined : value)),
  phone: optionalPhone,
  email: optionalEmail,
  idTypeCode: optionalText(30),
  idNumber: optionalText(50),
  politicallyExposed: z.boolean(),
});

export function RelatedPartiesPanel({ customer }: { customer: CustomerDetail }) {
  const canCapture = useCanCapture(customer);
  const refresh = useCustomerRefresh(customer.id);
  const [adding, setAdding] = useState(false);
  const parties = customer.relatedParties.filter((party) => party.active);
  const form = useForm<z.input<typeof partySchema>, unknown, z.output<typeof partySchema>>({
    resolver: zodResolver(partySchema),
    defaultValues: {
      fullName: '',
      role: 'DIRECTOR',
      ownershipPercent: '',
      nationality: 'GH',
      dateOfBirth: '',
      phone: '',
      email: '',
      idTypeCode: '',
      idNumber: '',
      politicallyExposed: false,
    },
  });

  return (
    <Section title="Directors, owners and signatories" action={canCapture ? <AddButton onClick={() => setAdding(true)} /> : null}>
      {parties.length === 0 ? (
        <EmptyState title="No related parties recorded" description="Record directors, beneficial owners and authorised signatories." />
      ) : (
        <ul className="divide-y">
          {parties.map((party) => (
            <li key={party.id} className="flex flex-wrap items-start justify-between gap-3 py-3 text-sm">
              <div className="grid gap-0.5">
                <p className="flex items-center gap-2 font-medium">
                  {party.fullName}
                  <Badge>{humanize(party.partyRole)}</Badge>
                  {party.politicallyExposed ? <Badge tone="danger">PEP</Badge> : null}
                </p>
                <p className="text-muted-foreground">
                  {party.ownershipPercent !== null ? `${party.ownershipPercent}% ownership` : 'No ownership recorded'}
                  {party.idNumberMasked ? ` · ${party.idTypeCode ?? 'ID'} ${party.idNumberMasked}` : ''}
                </p>
              </div>
              {canCapture ? (
                <ConfirmAction
                  label="Remove"
                  variant="ghost"
                  destructive
                  title="Remove related party?"
                  description="The record is deactivated and the change is audited."
                  confirmLabel="Remove"
                  onConfirm={() => bff(`/customers/${customer.id}/related-parties/${party.id}`, { method: 'DELETE' })}
                  onDone={refresh}
                />
              ) : null}
            </li>
          ))}
        </ul>
      )}
      <RecordForm
        open={adding}
        title="Add related party"
        onClose={() => setAdding(false)}
        form={form}
        fields={['fullName', 'role', 'ownershipPercent', 'nationality', 'dateOfBirth', 'phone', 'email', 'idTypeCode', 'idNumber']}
        submit={(values) => bff(`/customers/${customer.id}/related-parties`, { method: 'POST', body: values })}
        onSaved={refresh}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Full name" error={form.formState.errors.fullName?.message} required>
            {(control) => <Input {...control} {...form.register('fullName')} />}
          </FormField>
          <FormField label="Role" required>
            {(control) => (
              <Select {...control} {...form.register('role')}>
                <option value="DIRECTOR">Director</option>
                <option value="SHAREHOLDER">Shareholder</option>
                <option value="BENEFICIAL_OWNER">Beneficial owner</option>
                <option value="AUTHORISED_SIGNATORY">Authorised signatory</option>
                <option value="PARTNER">Partner</option>
                <option value="TRUSTEE">Trustee</option>
              </Select>
            )}
          </FormField>
          <FormField label="Ownership %" error={form.formState.errors.ownershipPercent?.message}>
            {(control) => <Input {...control} inputMode="decimal" {...form.register('ownershipPercent')} />}
          </FormField>
          <FormField label="Nationality" error={form.formState.errors.nationality?.message}>
            {(control) => <Input {...control} maxLength={2} {...form.register('nationality')} />}
          </FormField>
          <FormField label="Date of birth" error={form.formState.errors.dateOfBirth?.message}>
            {(control) => <Input {...control} type="date" {...form.register('dateOfBirth')} />}
          </FormField>
          <FormField label="Phone" error={form.formState.errors.phone?.message}>
            {(control) => <Input {...control} type="tel" {...form.register('phone')} />}
          </FormField>
          <FormField label="Email" error={form.formState.errors.email?.message}>
            {(control) => <Input {...control} type="email" {...form.register('email')} />}
          </FormField>
          <FormField label="ID type" error={form.formState.errors.idTypeCode?.message}>
            {(control) => <Input {...control} placeholder="e.g. GHANA_CARD" {...form.register('idTypeCode')} />}
          </FormField>
          <FormField label="ID number" error={form.formState.errors.idNumber?.message} hint="Encrypted at rest">
            {(control) => <Input {...control} autoComplete="off" {...form.register('idNumber')} />}
          </FormField>
          <Checkbox label="Politically exposed person" {...form.register('politicallyExposed')} />
        </div>
      </RecordForm>
    </Section>
  );
}

// ------------------------------------------------------------------------------------------------- helpers

function AddButton({ onClick }: { onClick: () => void }) {
  return (
    <Button size="sm" variant="outline" onClick={onClick}>
      <Plus aria-hidden="true" />
      Add
    </Button>
  );
}

function RecordForm<TInput extends FieldValues, TOutput extends FieldValues>({
  open,
  title,
  onClose,
  form,
  fields,
  submit,
  onSaved,
  children,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  form: UseFormReturn<TInput, unknown, TOutput>;
  fields: Path<TInput>[];
  submit: (values: TOutput) => Promise<unknown>;
  onSaved: () => void;
  children: React.ReactNode;
}) {
  const [failure, setFailure] = useState<string>();
  const save = useMutation({
    mutationFn: submit,
    onSuccess: () => {
      form.reset();
      onSaved();
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, fields)) {
        setFailure(errorMessage(error));
      }
    },
  });
  return (
    <Modal open={open} onClose={onClose} busy={save.isPending} title={title} className="max-w-2xl">
      <form
        className="grid gap-4"
        noValidate
        onSubmit={form.handleSubmit((values) => {
          setFailure(undefined);
          save.mutate(values);
        })}
      >
        {failure ? <Alert tone="danger">{failure}</Alert> : null}
        {children}
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
