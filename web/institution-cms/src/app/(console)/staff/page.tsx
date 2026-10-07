'use client';

import { bff, Permission, query, type IssuedCredential, type Page, type StaffCreated, type StaffSummary } from '@banking/api';
import { applyFieldErrors, errorMessage, OneTimeCredentialDialog } from '@banking/console';
import { Alert, Button, Checkbox, DataTable, FormField, Input, Modal, PageHeader, Select, StatusBadge } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useCan, useMe } from '@/lib/me';
import { useBranches, useRoleOptions } from '@/lib/queries';
import { optionalPhone, optionalText, requiredText, USERNAME } from '@/lib/schemas';

const staffSchema = z.object({
  employeeNumber: requiredText(30, 'Enter the employee number'),
  firstName: requiredText(100, 'Enter the first name'),
  lastName: requiredText(100, 'Enter the last name'),
  email: z.string().trim().max(254).pipe(z.email('Enter a valid email address')),
  phone: optionalPhone,
  jobTitle: optionalText(100),
  homeBranchId: z.string().uuid('Choose the home branch'),
  allBranchesAccess: z.boolean(),
  username: z.string().trim().toLowerCase().regex(USERNAME, 'Use 3-100 letters, digits or . _ @ -'),
  roleIds: z.array(z.string()).max(20),
});

export default function StaffPage() {
  const router = useRouter();
  const canCreate = useCan(Permission.staffCreate);
  const { branches, nameOf } = useBranches();
  const [draft, setDraft] = useState('');
  const [filters, setFilters] = useState({ q: '', status: '', branchId: '' });
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [issued, setIssued] = useState<IssuedCredential | null>(null);

  const staff = useQuery({
    queryKey: ['staff', filters, page],
    queryFn: ({ signal }) => bff<Page<StaffSummary>>(`/staff${query({ ...filters, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<StaffSummary, unknown>[] = [
    {
      header: 'Name',
      cell: ({ row }) => (
        <span className="grid">
          <span className="font-medium">
            {row.original.firstName} {row.original.lastName}
          </span>
          <span className="text-xs text-muted-foreground">{row.original.email}</span>
        </span>
      ),
    },
    { header: 'Employee no.', cell: ({ row }) => <span className="font-mono text-sm">{row.original.employeeNumber}</span> },
    { header: 'Job title', cell: ({ row }) => row.original.jobTitle ?? '—' },
    { header: 'Home branch', cell: ({ row }) => nameOf(row.original.homeBranchId) },
    { header: 'Scope', cell: ({ row }) => (row.original.allBranchesAccess ? 'All branches' : 'Home branch') },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  function search(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setFilters({ ...filters, q: draft.trim() });
  }

  return (
    <>
      <PageHeader
        title="Staff"
        actions={
          canCreate ? (
            <Button onClick={() => setCreating(true)}>
              <Plus aria-hidden="true" />
              New staff member
            </Button>
          ) : null
        }
      />
      <div className="mb-4 grid gap-3 rounded-lg border bg-card p-4 sm:grid-cols-4">
        <form onSubmit={search} className="sm:col-span-2">
          <FormField label="Search">
            {(control) => <Input {...control} type="search" placeholder="Name, email or employee number" value={draft} onChange={(event) => setDraft(event.target.value)} />}
          </FormField>
        </form>
        <FormField label="Status">
          {(control) => (
            <Select
              {...control}
              value={filters.status}
              onChange={(event) => {
                setPage(0);
                setFilters({ ...filters, status: event.target.value });
              }}
            >
              <option value="">Any</option>
              <option value="ACTIVE">Active</option>
              <option value="SUSPENDED">Suspended</option>
              <option value="TERMINATED">Terminated</option>
            </Select>
          )}
        </FormField>
        <FormField label="Branch">
          {(control) => (
            <Select
              {...control}
              value={filters.branchId}
              onChange={(event) => {
                setPage(0);
                setFilters({ ...filters, branchId: event.target.value });
              }}
            >
              <option value="">All in scope</option>
              {branches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name}
                </option>
              ))}
            </Select>
          )}
        </FormField>
      </div>
      {staff.isError ? <Alert tone="danger" className="mb-4">{errorMessage(staff.error)}</Alert> : null}
      <DataTable
        caption="Staff"
        columns={columns}
        data={staff.data?.items}
        loading={staff.isLoading}
        pageInfo={staff.data}
        onPageChange={setPage}
        onRowClick={(member) => router.push(`/staff/${member.id}`)}
        getRowId={(member) => member.id}
        emptyTitle="No staff found"
      />
      {creating ? (
        <CreateStaffDialog
          onClose={() => setCreating(false)}
          onCreated={(created) => {
            setCreating(false);
            setIssued(created.credential);
          }}
        />
      ) : null}
      <OneTimeCredentialDialog title="Staff member created" credential={issued} onClose={() => setIssued(null)} />
    </>
  );
}

function CreateStaffDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (created: StaffCreated) => void }) {
  const queryClient = useQueryClient();
  const me = useMe();
  const { activeBranches } = useBranches();
  const roles = useRoleOptions();
  const canAssign = useCan(Permission.roleAssign);
  const [failure, setFailure] = useState<string>();
  const form = useForm<z.input<typeof staffSchema>, unknown, z.output<typeof staffSchema>>({
    resolver: zodResolver(staffSchema),
    defaultValues: {
      employeeNumber: '',
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      jobTitle: '',
      homeBranchId: me.homeBranchId,
      allBranchesAccess: false,
      username: '',
      roleIds: [],
    },
  });
  const create = useMutation({
    mutationFn: (values: z.output<typeof staffSchema>) => bff<StaffCreated>('/staff', { method: 'POST', body: values }),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ['staff'] });
      onCreated(created);
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, ['employeeNumber', 'firstName', 'lastName', 'email', 'phone', 'homeBranchId', 'username', 'roleIds'])) {
        setFailure(errorMessage(error));
      }
    },
  });
  const { errors } = form.formState;
  const assignable = (roles.data ?? []).filter((role) => role.status === 'ACTIVE');

  return (
    <Modal open onClose={onClose} busy={create.isPending} title="New staff member" description="A temporary password is generated; it must be changed at first sign-in." className="max-w-2xl">
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={form.handleSubmit((values) => create.mutate(values))}>
        {failure ? <Alert tone="danger" className="sm:col-span-2">{failure}</Alert> : null}
        <FormField label="First name" error={errors.firstName?.message} required>
          {(control) => <Input {...control} {...form.register('firstName')} />}
        </FormField>
        <FormField label="Last name" error={errors.lastName?.message} required>
          {(control) => <Input {...control} {...form.register('lastName')} />}
        </FormField>
        <FormField label="Email" error={errors.email?.message} required>
          {(control) => <Input {...control} type="email" {...form.register('email')} />}
        </FormField>
        <FormField label="Phone" error={errors.phone?.message}>
          {(control) => <Input {...control} type="tel" {...form.register('phone')} />}
        </FormField>
        <FormField label="Employee number" error={errors.employeeNumber?.message} required>
          {(control) => <Input {...control} {...form.register('employeeNumber')} />}
        </FormField>
        <FormField label="Job title" error={errors.jobTitle?.message}>
          {(control) => <Input {...control} {...form.register('jobTitle')} />}
        </FormField>
        <FormField label="Username" error={errors.username?.message} required>
          {(control) => <Input {...control} autoCapitalize="none" spellCheck={false} {...form.register('username')} />}
        </FormField>
        <FormField label="Home branch" error={errors.homeBranchId?.message} required>
          {(control) => (
            <Select {...control} {...form.register('homeBranchId')}>
              {activeBranches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name} ({branch.code})
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <Checkbox className="sm:col-span-2" label="Access to all branches (otherwise the home branch only)" {...form.register('allBranchesAccess')} />
        {canAssign && assignable.length > 0 ? (
          <fieldset className="grid gap-2 sm:col-span-2">
            <legend className="mb-1 text-sm font-medium">Roles</legend>
            <div className="grid gap-2 sm:grid-cols-2">
              {assignable.map((role) => (
                <Checkbox key={role.id} value={role.id} label={role.name} {...form.register('roleIds')} />
              ))}
            </div>
            {errors.roleIds?.message ? <p className="text-xs text-destructive">{errors.roleIds.message}</p> : null}
          </fieldset>
        ) : null}
        <div className="flex justify-end gap-2 sm:col-span-2">
          <Button variant="outline" onClick={onClose} disabled={create.isPending}>
            Cancel
          </Button>
          <Button type="submit" loading={create.isPending}>
            Create staff member
          </Button>
        </div>
      </form>
    </Modal>
  );
}
