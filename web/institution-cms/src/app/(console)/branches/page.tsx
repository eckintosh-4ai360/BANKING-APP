'use client';

import { bff, Permission, query, type Branch, type Page } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import { Alert, Button, DataTable, FormField, Input, Modal, PageHeader, Select, StatusBadge, Textarea, formatDate, humanize } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useCan } from '@/lib/me';
import { BRANCH_CODE, DIGITAL_ADDRESS, optionalDate, optionalEmail, optionalPattern, optionalPhone, optionalText, requiredText } from '@/lib/schemas';

const branchSchema = z.object({
  code: z.string().trim().toUpperCase().regex(BRANCH_CODE, 'Use 1-20 upper-case letters, digits or hyphens'),
  name: requiredText(150, 'Enter the branch name'),
  branchType: z.enum(['BRANCH', 'AGENCY']),
  phone: optionalPhone,
  email: optionalEmail,
  addressLine1: optionalText(200),
  addressLine2: optionalText(200),
  city: optionalText(100),
  region: optionalText(100),
  digitalAddress: optionalPattern(DIGITAL_ADDRESS, 'e.g. GA-123-4567'),
  openedOn: optionalDate,
});

type BranchInput = z.input<typeof branchSchema>;
type BranchValues = z.output<typeof branchSchema>;

export default function BranchesPage() {
  const canManage = useCan(Permission.branchManage);
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Branch | 'new' | null>(null);
  const [statusOf, setStatusOf] = useState<Branch | null>(null);

  const branches = useQuery({
    queryKey: ['branches', { status, page }],
    queryFn: ({ signal }) => bff<Page<Branch>>(`/branches${query({ status, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<Branch, unknown>[] = [
    {
      header: 'Branch',
      cell: ({ row }) => (
        <span className="grid">
          <span className="font-medium">{row.original.name}</span>
          <span className="font-mono text-xs text-muted-foreground">{row.original.code}</span>
        </span>
      ),
    },
    { header: 'Type', cell: ({ row }) => humanize(row.original.branchType) },
    { header: 'Location', cell: ({ row }) => [row.original.city, row.original.region].filter(Boolean).join(', ') || '—' },
    { header: 'Phone', cell: ({ row }) => row.original.phone ?? '—' },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    { header: 'Opened', cell: ({ row }) => formatDate(row.original.openedOn) },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) =>
        canManage ? (
          <span className="flex justify-end gap-1">
            <Button size="sm" variant="ghost" onClick={() => setEditing(row.original)}>
              Edit
            </Button>
            {row.original.branchType !== 'HEAD_OFFICE' ? (
              <Button size="sm" variant="ghost" onClick={() => setStatusOf(row.original)}>
                Status
              </Button>
            ) : null}
          </span>
        ) : null,
    },
  ];

  return (
    <>
      <PageHeader
        title="Branches"
        actions={
          canManage ? (
            <Button onClick={() => setEditing('new')}>
              <Plus aria-hidden="true" />
              New branch
            </Button>
          ) : null
        }
      />
      <div className="mb-4 flex max-w-xs">
        <FormField label="Status" className="w-full">
          {(control) => (
            <Select
              {...control}
              value={status}
              onChange={(event) => {
                setStatus(event.target.value);
                setPage(0);
              }}
            >
              <option value="">Any</option>
              <option value="ACTIVE">Active</option>
              <option value="INACTIVE">Inactive</option>
              <option value="CLOSED">Closed</option>
            </Select>
          )}
        </FormField>
      </div>
      {branches.isError ? <Alert tone="danger" className="mb-4">{errorMessage(branches.error)}</Alert> : null}
      <DataTable caption="Branches" columns={columns} data={branches.data?.items} loading={branches.isLoading} pageInfo={branches.data} onPageChange={setPage} getRowId={(branch) => branch.id} emptyTitle="No branches" />
      {editing ? <BranchDialog branch={editing === 'new' ? null : editing} onClose={() => setEditing(null)} /> : null}
      {statusOf ? <BranchStatusDialog branch={statusOf} onClose={() => setStatusOf(null)} /> : null}
    </>
  );
}

function BranchDialog({ branch, onClose }: { branch: Branch | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string>();
  const form = useForm<BranchInput, unknown, BranchValues>({
    resolver: zodResolver(branchSchema),
    defaultValues: {
      code: branch?.code ?? '',
      name: branch?.name ?? '',
      branchType: branch?.branchType === 'AGENCY' ? 'AGENCY' : 'BRANCH',
      phone: branch?.phone ?? '',
      email: branch?.email ?? '',
      addressLine1: branch?.addressLine1 ?? '',
      addressLine2: branch?.addressLine2 ?? '',
      city: branch?.city ?? '',
      region: branch?.region ?? '',
      digitalAddress: branch?.digitalAddress ?? '',
      openedOn: branch?.openedOn ?? '',
    },
  });
  const save = useMutation({
    mutationFn: (values: BranchValues) => {
      if (!branch) {
        return bff<Branch>('/branches', { method: 'POST', body: values });
      }
      // Code and type are fixed after creation; the version guards against overwriting a concurrent change.
      const { code: _code, branchType: _type, ...changes } = values;
      return bff<Branch>(`/branches/${branch.id}`, { method: 'PUT', body: { ...changes, version: branch.version } });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['branches'] });
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, ['code', 'name', 'phone', 'email', 'digitalAddress', 'openedOn'])) {
        setFailure(errorMessage(error));
      }
    },
  });
  const { errors } = form.formState;
  return (
    <Modal open onClose={onClose} busy={save.isPending} title={branch ? `Edit ${branch.name}` : 'New branch'} className="max-w-2xl">
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
        {failure ? <Alert tone="danger" className="sm:col-span-2">{failure}</Alert> : null}
        <FormField label="Code" error={errors.code?.message} required>
          {(control) => <Input {...control} disabled={Boolean(branch)} className="uppercase" {...form.register('code')} />}
        </FormField>
        <FormField label="Type" required>
          {(control) => (
            <Select {...control} disabled={Boolean(branch)} {...form.register('branchType')}>
              <option value="BRANCH">Branch</option>
              <option value="AGENCY">Agency</option>
            </Select>
          )}
        </FormField>
        <FormField label="Name" error={errors.name?.message} required className="sm:col-span-2">
          {(control) => <Input {...control} {...form.register('name')} />}
        </FormField>
        <FormField label="Phone" error={errors.phone?.message}>
          {(control) => <Input {...control} type="tel" {...form.register('phone')} />}
        </FormField>
        <FormField label="Email" error={errors.email?.message}>
          {(control) => <Input {...control} type="email" {...form.register('email')} />}
        </FormField>
        <FormField label="Address line 1" error={errors.addressLine1?.message} className="sm:col-span-2">
          {(control) => <Input {...control} {...form.register('addressLine1')} />}
        </FormField>
        <FormField label="Address line 2" error={errors.addressLine2?.message} className="sm:col-span-2">
          {(control) => <Input {...control} {...form.register('addressLine2')} />}
        </FormField>
        <FormField label="City / town" error={errors.city?.message}>
          {(control) => <Input {...control} {...form.register('city')} />}
        </FormField>
        <FormField label="Region" error={errors.region?.message}>
          {(control) => <Input {...control} {...form.register('region')} />}
        </FormField>
        <FormField label="Digital address" error={errors.digitalAddress?.message}>
          {(control) => <Input {...control} {...form.register('digitalAddress')} />}
        </FormField>
        <FormField label="Opened on" error={errors.openedOn?.message}>
          {(control) => <Input {...control} type="date" {...form.register('openedOn')} />}
        </FormField>
        <div className="flex justify-end gap-2 sm:col-span-2">
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={save.isPending}>
            {branch ? 'Save changes' : 'Create branch'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function BranchStatusDialog({ branch, onClose }: { branch: Branch; onClose: () => void }) {
  const queryClient = useQueryClient();
  const options = ['ACTIVE', 'INACTIVE', 'CLOSED'].filter((status) => status !== branch.status);
  const [status, setStatus] = useState(options[0] ?? 'ACTIVE');
  const [reason, setReason] = useState('');
  const change = useMutation({
    mutationFn: () => bff(`/branches/${branch.id}/status`, { method: 'POST', body: { status, reason: reason.trim(), version: branch.version } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['branches'] });
      onClose();
    },
  });
  return (
    <Modal
      open
      onClose={onClose}
      busy={change.isPending}
      title={`Change status of ${branch.name}`}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={change.isPending}>
            Cancel
          </Button>
          <Button variant={status === 'ACTIVE' ? 'primary' : 'destructive'} loading={change.isPending} disabled={reason.trim().length < 3} onClick={() => change.mutate()}>
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
        <FormField label="Reason" required>
          {(control) => <Textarea {...control} value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
