'use client';

import { bff, Permission, type FeatureState, type Institution, type KycTier } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import { Alert, Badge, Button, Card, CardContent, CardFooter, CardHeader, CardTitle, Checkbox, DetailList, FormField, Input, Modal, PageHeader, Skeleton, Tabs, Textarea } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { CustomerAppSettings } from '@/components/channel/customer-app-settings';
import { useCan } from '@/lib/me';
import { useIdentificationTypes, useKycTiers, type IdentificationType } from '@/lib/queries';
import { DIGITAL_ADDRESS, optionalEmail, optionalPattern, optionalPhone, optionalText, requiredText } from '@/lib/schemas';

const INSTITUTION_KEY = ['institution'] as const;

export default function SettingsPage() {
  const canViewInstitution = useCan(Permission.institutionView);
  const canViewKyc = useCan(Permission.kycView);
  const canViewIdTypes = useCan(Permission.customerView);
  const canViewChannel = useCan(Permission.settingsView, Permission.settingsManage);
  const tabs = [
    ...(canViewInstitution ? [{ id: 'institution', label: 'Institution' }, { id: 'features', label: 'Features' }] : []),
    ...(canViewKyc ? [{ id: 'tiers', label: 'KYC tiers' }] : []),
    ...(canViewIdTypes ? [{ id: 'id-types', label: 'Identification types' }] : []),
    ...(canViewChannel ? [{ id: 'customer-app', label: 'Customer app' }] : []),
  ];
  const [tab, setTab] = useState(tabs[0]?.id ?? '');

  return (
    <>
      <PageHeader title="Settings" description="Institution-level configuration. Every change is audited." />
      {tabs.length === 0 ? <Alert tone="info">You don't have access to any settings.</Alert> : <Tabs tabs={tabs} active={tab} onChange={setTab} />}
      {tab === 'institution' ? <InstitutionSettings /> : null}
      {tab === 'features' ? <FeatureSettings /> : null}
      {tab === 'tiers' ? <TierSettings /> : null}
      {tab === 'id-types' ? <IdentificationTypeSettings /> : null}
      {tab === 'customer-app' ? <CustomerAppSettings /> : null}
    </>
  );
}

function useInstitution() {
  return useQuery({ queryKey: INSTITUTION_KEY, queryFn: ({ signal }) => bff<Institution>('/institution', { signal }) });
}

// ---------------------------------------------------------------------------------------------- institution

const profileSchema = z.object({
  displayName: requiredText(120, 'Enter the display name'),
  contactEmail: optionalEmail,
  contactPhone: optionalPhone,
  supportEmail: optionalEmail,
  supportPhone: optionalPhone,
  websiteUrl: optionalPattern(/^https:\/\/\S+$/, 'Use an https:// address'),
  addressLine1: optionalText(200),
  addressLine2: optionalText(200),
  city: optionalText(100),
  region: optionalText(100),
  digitalAddress: optionalPattern(DIGITAL_ADDRESS, 'e.g. GA-123-4567'),
});

const brandingSchema = z.object({
  logoUrl: optionalPattern(/^https:\/\/\S+$/, 'Use an https:// address'),
  primaryColor: z.string().regex(/^#[0-9A-Fa-f]{6}$/, 'Use a colour like #0B3B60'),
  secondaryColor: z.string().regex(/^#[0-9A-Fa-f]{6}$/, 'Use a colour like #0B3B60'),
  smsSenderId: optionalPattern(/^[A-Za-z0-9][A-Za-z0-9 ]{0,10}$/, 'Up to 11 letters, digits or spaces'),
  emailSenderName: optionalText(100),
  emailSenderAddress: optionalEmail,
});

function InstitutionSettings() {
  const institution = useInstitution();
  const canManage = useCan(Permission.institutionManage);
  const [editing, setEditing] = useState<'profile' | 'branding' | null>(null);
  if (institution.isPending) {
    return <Skeleton className="h-64" />;
  }
  if (institution.isError) {
    return <Alert tone="danger">{errorMessage(institution.error)}</Alert>;
  }
  const { institution: tenant, profile } = institution.data;
  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle>Institution</CardTitle>
        </CardHeader>
        <CardContent>
          <DetailList
            items={[
              { label: 'Display name', value: tenant.displayName },
              { label: 'Legal name', value: tenant.legalName },
              { label: 'Institution code', value: <span className="font-mono">{tenant.code}</span> },
              { label: 'Licence number', value: tenant.licenceNumber },
              { label: 'Country / currency', value: `${tenant.countryCode} · ${tenant.baseCurrency}` },
              { label: 'Time zone / locale', value: `${tenant.timezone} · ${tenant.locale}` },
              { label: 'Contact', value: [profile.contactEmail, profile.contactPhone].filter(Boolean).join(' · ') },
              { label: 'Customer support', value: [profile.supportEmail, profile.supportPhone].filter(Boolean).join(' · ') },
              { label: 'Website', value: profile.websiteUrl },
              { label: 'Address', value: [profile.addressLine1, profile.addressLine2, profile.city, profile.region, profile.digitalAddress].filter(Boolean).join(', ') },
            ]}
          />
          <p className="mt-4 text-xs text-muted-foreground">Legal name, code, licence, country and currency are managed by the platform operator.</p>
        </CardContent>
        {canManage ? (
          <CardFooter>
            <Button variant="outline" onClick={() => setEditing('profile')}>
              Edit profile
            </Button>
          </CardFooter>
        ) : null}
      </Card>
      <Card>
        <CardHeader>
          <CardTitle>Branding & messaging</CardTitle>
        </CardHeader>
        <CardContent>
          <DetailList
            items={[
              {
                label: 'Colours',
                value: (
                  <span className="flex items-center gap-2">
                    <ColourSwatch colour={profile.primaryColor} /> {profile.primaryColor}
                    <ColourSwatch colour={profile.secondaryColor} /> {profile.secondaryColor}
                  </span>
                ),
              },
              { label: 'Logo URL', value: profile.logoUrl },
              { label: 'SMS sender ID', value: profile.smsSenderId },
              { label: 'Email sender', value: [profile.emailSenderName, profile.emailSenderAddress].filter(Boolean).join(' · ') },
            ]}
          />
        </CardContent>
        {canManage ? (
          <CardFooter>
            <Button variant="outline" onClick={() => setEditing('branding')}>
              Edit branding
            </Button>
          </CardFooter>
        ) : null}
      </Card>
      {editing === 'profile' ? <ProfileDialog institution={institution.data} onClose={() => setEditing(null)} /> : null}
      {editing === 'branding' ? <BrandingDialog institution={institution.data} onClose={() => setEditing(null)} /> : null}
    </div>
  );
}

function ColourSwatch({ colour }: { colour: string }) {
  // Colours are validated hex values from the backend; they are drawn with an SVG fill rather than inline styles so
  // the page stays within the Content-Security-Policy.
  return (
    <svg width="16" height="16" aria-hidden="true" className="rounded border">
      <rect width="16" height="16" fill={/^#[0-9A-Fa-f]{6}$/.test(colour) ? colour : 'transparent'} />
    </svg>
  );
}

function ProfileDialog({ institution, onClose }: { institution: Institution; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string>();
  const { profile, institution: tenant } = institution;
  const form = useForm<z.input<typeof profileSchema>, unknown, z.output<typeof profileSchema>>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      displayName: tenant.displayName,
      contactEmail: profile.contactEmail ?? '',
      contactPhone: profile.contactPhone ?? '',
      supportEmail: profile.supportEmail ?? '',
      supportPhone: profile.supportPhone ?? '',
      websiteUrl: profile.websiteUrl ?? '',
      addressLine1: profile.addressLine1 ?? '',
      addressLine2: profile.addressLine2 ?? '',
      city: profile.city ?? '',
      region: profile.region ?? '',
      digitalAddress: profile.digitalAddress ?? '',
    },
  });
  const save = useMutation({
    mutationFn: (values: z.output<typeof profileSchema>) => bff('/institution/profile', { method: 'PUT', body: { ...values, version: profile.version } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: INSTITUTION_KEY });
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, ['displayName', 'contactEmail', 'contactPhone', 'supportEmail', 'supportPhone', 'websiteUrl', 'digitalAddress'])) {
        setFailure(errorMessage(error));
      }
    },
  });
  const { errors } = form.formState;
  const field = (name: keyof z.input<typeof profileSchema>, label: string, props: { type?: string; required?: boolean; wide?: boolean } = {}) => (
    <FormField label={label} error={errors[name]?.message} required={props.required} className={props.wide ? 'sm:col-span-2' : undefined}>
      {(control) => <Input {...control} type={props.type} {...form.register(name)} />}
    </FormField>
  );
  return (
    <Modal open onClose={onClose} busy={save.isPending} title="Edit institution profile" className="max-w-2xl">
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
        {failure ? <Alert tone="danger" className="sm:col-span-2">{failure}</Alert> : null}
        {field('displayName', 'Display name', { required: true, wide: true })}
        {field('contactEmail', 'Contact email', { type: 'email' })}
        {field('contactPhone', 'Contact phone', { type: 'tel' })}
        {field('supportEmail', 'Support email', { type: 'email' })}
        {field('supportPhone', 'Support phone', { type: 'tel' })}
        {field('websiteUrl', 'Website', { type: 'url', wide: true })}
        {field('addressLine1', 'Address line 1', { wide: true })}
        {field('addressLine2', 'Address line 2', { wide: true })}
        {field('city', 'City / town')}
        {field('region', 'Region')}
        {field('digitalAddress', 'Digital address')}
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

function BrandingDialog({ institution, onClose }: { institution: Institution; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string>();
  const { profile } = institution;
  const form = useForm<z.input<typeof brandingSchema>, unknown, z.output<typeof brandingSchema>>({
    resolver: zodResolver(brandingSchema),
    defaultValues: {
      logoUrl: profile.logoUrl ?? '',
      primaryColor: profile.primaryColor,
      secondaryColor: profile.secondaryColor,
      smsSenderId: profile.smsSenderId ?? '',
      emailSenderName: profile.emailSenderName ?? '',
      emailSenderAddress: profile.emailSenderAddress ?? '',
    },
  });
  const save = useMutation({
    mutationFn: (values: z.output<typeof brandingSchema>) => bff('/institution/branding', { method: 'PUT', body: { ...values, version: profile.version } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: INSTITUTION_KEY });
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, ['logoUrl', 'primaryColor', 'secondaryColor', 'smsSenderId', 'emailSenderName', 'emailSenderAddress'])) {
        setFailure(errorMessage(error));
      }
    },
  });
  const { errors } = form.formState;
  return (
    <Modal open onClose={onClose} busy={save.isPending} title="Edit branding" description="Used by the customer app and in messages sent to customers.">
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
        {failure ? <Alert tone="danger" className="sm:col-span-2">{failure}</Alert> : null}
        <FormField label="Primary colour" error={errors.primaryColor?.message} required>
          {(control) => <Input {...control} type="color" className="h-10 p-1" {...form.register('primaryColor')} />}
        </FormField>
        <FormField label="Secondary colour" error={errors.secondaryColor?.message} required>
          {(control) => <Input {...control} type="color" className="h-10 p-1" {...form.register('secondaryColor')} />}
        </FormField>
        <FormField label="Logo URL" error={errors.logoUrl?.message} className="sm:col-span-2">
          {(control) => <Input {...control} type="url" placeholder="https://" {...form.register('logoUrl')} />}
        </FormField>
        <FormField label="SMS sender ID" error={errors.smsSenderId?.message}>
          {(control) => <Input {...control} maxLength={11} {...form.register('smsSenderId')} />}
        </FormField>
        <FormField label="Email sender name" error={errors.emailSenderName?.message}>
          {(control) => <Input {...control} {...form.register('emailSenderName')} />}
        </FormField>
        <FormField label="Email sender address" error={errors.emailSenderAddress?.message} className="sm:col-span-2">
          {(control) => <Input {...control} type="email" {...form.register('emailSenderAddress')} />}
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

// ------------------------------------------------------------------------------------------------- features

function FeatureSettings() {
  const queryClient = useQueryClient();
  const institution = useInstitution();
  const canManage = useCan(Permission.institutionManage);
  const toggle = useMutation({
    mutationFn: (feature: FeatureState) => bff<FeatureState>(`/institution/features/${feature.code}`, { method: 'PUT', body: { enabled: !feature.enabled } }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: INSTITUTION_KEY }),
  });
  if (institution.isPending) {
    return <Skeleton className="h-64" />;
  }
  if (institution.isError) {
    return <Alert tone="danger">{errorMessage(institution.error)}</Alert>;
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>Features</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-3">
        {toggle.isError ? <Alert tone="danger">{errorMessage(toggle.error)}</Alert> : null}
        <p className="text-sm text-muted-foreground">Features must be licensed by the platform operator before you can switch them on.</p>
        <ul className="divide-y">
          {institution.data.features.map((feature) => (
            <li key={feature.code} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="grid gap-0.5">
                <p className="flex items-center gap-2 font-medium">
                  {feature.name}
                  {!feature.licensed ? <Badge>Not licensed</Badge> : feature.enabled ? <Badge tone="success">On</Badge> : <Badge tone="neutral">Off</Badge>}
                </p>
                <p className="text-sm text-muted-foreground">{feature.description}</p>
              </div>
              {canManage && feature.licensed ? (
                <Button size="sm" variant={feature.enabled ? 'outline' : 'primary'} loading={toggle.isPending && toggle.variables?.code === feature.code} disabled={toggle.isPending} onClick={() => toggle.mutate(feature)}>
                  {feature.enabled ? 'Switch off' : 'Switch on'}
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

// ------------------------------------------------------------------------------------------------ KYC tiers

const TIER_REQUIREMENTS: { key: keyof KycTier; label: string }[] = [
  { key: 'requiresIdentification', label: 'Identification' },
  { key: 'requiresIdDocument', label: 'ID document images' },
  { key: 'requiresSelfie', label: 'Selfie' },
  { key: 'requiresAddress', label: 'Address' },
  { key: 'requiresProofOfAddress', label: 'Proof of address' },
  { key: 'requiresIdentityVerification', label: 'Electronic identity verification' },
  { key: 'requiresNextOfKin', label: 'Next of kin' },
  { key: 'requiresSignature', label: 'Signature' },
  { key: 'requiresEmploymentInfo', label: 'Employment information' },
];

function TierSettings() {
  const tiers = useKycTiers();
  const canManage = useCan(Permission.settingsManage);
  const [editing, setEditing] = useState<KycTier | null>(null);
  if (tiers.isPending) {
    return <Skeleton className="h-64" />;
  }
  if (tiers.isError) {
    return <Alert tone="danger">{errorMessage(tiers.error)}</Alert>;
  }
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      {[...tiers.data]
        .sort((a, b) => a.tierRank - b.tierRank)
        .map((tier) => (
          <Card key={tier.code}>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                {tier.name} <Badge>{tier.code}</Badge>
                {!tier.active ? <Badge tone="warning">Inactive</Badge> : null}
              </CardTitle>
              {tier.description ? <p className="text-sm text-muted-foreground">{tier.description}</p> : null}
            </CardHeader>
            <CardContent>
              <ul className="grid gap-1 text-sm">
                {TIER_REQUIREMENTS.filter((requirement) => tier[requirement.key]).map((requirement) => (
                  <li key={requirement.key}>• {requirement.label}</li>
                ))}
              </ul>
            </CardContent>
            {canManage ? (
              <CardFooter>
                <Button size="sm" variant="outline" onClick={() => setEditing(tier)}>
                  Edit
                </Button>
              </CardFooter>
            ) : null}
          </Card>
        ))}
      {editing ? <TierDialog tier={editing} onClose={() => setEditing(null)} /> : null}
    </div>
  );
}

function TierDialog({ tier, onClose }: { tier: KycTier; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<KycTier>(tier);
  const save = useMutation({
    mutationFn: () => {
      const { code: _code, tierRank: _rank, ...body } = draft;
      return bff<KycTier>(`/kyc/tiers/${tier.code}`, { method: 'PUT', body });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['kyc-tiers'] });
      onClose();
    },
  });
  return (
    <Modal
      open
      onClose={onClose}
      busy={save.isPending}
      title={`Edit tier ${tier.code}`}
      description="Changes apply to cases opened or decided from now on; customers already verified keep their tier."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button loading={save.isPending} onClick={() => save.mutate()}>
            Save
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {save.isError ? <Alert tone="danger">{errorMessage(save.error)}</Alert> : null}
        <FormField label="Name" required>
          {(control) => <Input {...control} value={draft.name} maxLength={100} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />}
        </FormField>
        <FormField label="Description">
          {(control) => <Textarea {...control} value={draft.description ?? ''} maxLength={500} onChange={(event) => setDraft({ ...draft, description: event.target.value || null })} />}
        </FormField>
        <fieldset className="grid gap-2 sm:grid-cols-2">
          <legend className="mb-1 text-sm font-medium">Requirements</legend>
          {TIER_REQUIREMENTS.map((requirement) => (
            <Checkbox
              key={requirement.key}
              label={requirement.label}
              checked={Boolean(draft[requirement.key])}
              onChange={(event) => setDraft({ ...draft, [requirement.key]: event.target.checked })}
            />
          ))}
        </fieldset>
        <Checkbox label="Tier is active" checked={draft.active} onChange={(event) => setDraft({ ...draft, active: event.target.checked })} />
      </div>
    </Modal>
  );
}

// -------------------------------------------------------------------------------------- identification types

const idTypeSchema = z.object({
  code: z.string().trim().toUpperCase().regex(/^[A-Z][A-Z0-9_]{1,29}$/, 'Use 2-30 upper-case letters, digits or underscores'),
  name: requiredText(100, 'Enter a name'),
  appliesTo: z.enum(['INDIVIDUAL', 'BUSINESS', 'ANY']),
  formatRegex: optionalText(200).refine((value) => {
    if (!value) {
      return true;
    }
    try {
      new RegExp(value);
      return true;
    } catch {
      return false;
    }
  }, 'Not a valid regular expression'),
  formatHint: optionalText(100),
  requiresExpiry: z.boolean(),
  supportsElectronicVerification: z.boolean(),
  active: z.boolean(),
  sortOrder: z
    .string()
    .regex(/^\d{1,4}$/, 'Enter a whole number')
    .transform((value) => Number.parseInt(value, 10)),
});

function IdentificationTypeSettings() {
  const types = useIdentificationTypes();
  const canManage = useCan(Permission.settingsManage);
  const [editing, setEditing] = useState<IdentificationType | 'new' | null>(null);
  if (types.isPending) {
    return <Skeleton className="h-64" />;
  }
  if (types.isError) {
    return <Alert tone="danger">{errorMessage(types.error)}</Alert>;
  }
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between">
        <CardTitle>Identification types</CardTitle>
        {canManage ? (
          <Button size="sm" onClick={() => setEditing('new')}>
            Add type
          </Button>
        ) : null}
      </CardHeader>
      <CardContent>
        <ul className="divide-y">
          {[...types.data]
            .sort((a, b) => a.sortOrder - b.sortOrder)
            .map((type) => (
              <li key={type.code} className="flex flex-wrap items-center justify-between gap-3 py-3 text-sm">
                <div className="grid gap-0.5">
                  <p className="flex items-center gap-2 font-medium">
                    {type.name} <Badge>{type.code}</Badge>
                    {!type.active ? <Badge tone="warning">Inactive</Badge> : null}
                    {type.supportsElectronicVerification ? <Badge tone="info">Electronic verification</Badge> : null}
                  </p>
                  <p className="text-muted-foreground">
                    For {type.appliesTo.toLowerCase()} customers
                    {type.formatHint ? ` · format ${type.formatHint}` : ''}
                    {type.requiresExpiry ? ' · expiry required' : ''}
                  </p>
                </div>
                {canManage ? (
                  <Button size="sm" variant="ghost" onClick={() => setEditing(type)}>
                    Edit
                  </Button>
                ) : null}
              </li>
            ))}
        </ul>
      </CardContent>
      {editing ? <IdTypeDialog type={editing === 'new' ? null : editing} onClose={() => setEditing(null)} /> : null}
    </Card>
  );
}

function IdTypeDialog({ type, onClose }: { type: IdentificationType | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string>();
  const form = useForm<z.input<typeof idTypeSchema>, unknown, z.output<typeof idTypeSchema>>({
    resolver: zodResolver(idTypeSchema),
    defaultValues: {
      code: type?.code ?? '',
      name: type?.name ?? '',
      appliesTo: type?.appliesTo ?? 'INDIVIDUAL',
      formatRegex: type?.formatRegex ?? '',
      formatHint: type?.formatHint ?? '',
      requiresExpiry: type?.requiresExpiry ?? false,
      supportsElectronicVerification: type?.supportsElectronicVerification ?? false,
      active: type?.active ?? true,
      sortOrder: String(type?.sortOrder ?? 100),
    },
  });
  const save = useMutation({
    mutationFn: (values: z.output<typeof idTypeSchema>) =>
      type
        ? bff(`/kyc/identification-types/${type.code}`, { method: 'PUT', body: { ...values, version: type.version } })
        : bff('/kyc/identification-types', { method: 'POST', body: values }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['identification-types'] });
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, ['code', 'name', 'appliesTo', 'formatRegex', 'formatHint', 'sortOrder'])) {
        setFailure(errorMessage(error));
      }
    },
  });
  const { errors } = form.formState;
  return (
    <Modal open onClose={onClose} busy={save.isPending} title={type ? `Edit ${type.name}` : 'Add identification type'} className="max-w-2xl">
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
        {failure ? <Alert tone="danger" className="sm:col-span-2">{failure}</Alert> : null}
        <FormField label="Code" error={errors.code?.message} required>
          {(control) => <Input {...control} disabled={Boolean(type)} className="uppercase" {...form.register('code')} />}
        </FormField>
        <FormField label="Name" error={errors.name?.message} required>
          {(control) => <Input {...control} {...form.register('name')} />}
        </FormField>
        <FormField label="Applies to" required>
          {(control) => (
            <select {...control} className="h-9 rounded-md border border-input bg-card px-3 text-sm" {...form.register('appliesTo')}>
              <option value="INDIVIDUAL">Individuals</option>
              <option value="BUSINESS">Businesses</option>
              <option value="ANY">Any customer</option>
            </select>
          )}
        </FormField>
        <FormField label="Sort order" error={errors.sortOrder?.message}>
          {(control) => <Input {...control} inputMode="numeric" {...form.register('sortOrder')} />}
        </FormField>
        <FormField label="Format (regular expression)" error={errors.formatRegex?.message} hint="e.g. ^GHA-[0-9]{9}-[0-9]$">
          {(control) => <Input {...control} className="font-mono" {...form.register('formatRegex')} />}
        </FormField>
        <FormField label="Format hint" error={errors.formatHint?.message} hint="Shown to staff, e.g. GHA-123456789-0">
          {(control) => <Input {...control} {...form.register('formatHint')} />}
        </FormField>
        <Checkbox label="Expiry date required" {...form.register('requiresExpiry')} />
        <Checkbox label="Supports electronic verification" {...form.register('supportsElectronicVerification')} />
        <Checkbox label="Active" {...form.register('active')} />
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
