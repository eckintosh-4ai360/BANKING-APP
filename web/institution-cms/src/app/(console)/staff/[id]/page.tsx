'use client';

import { bff, Permission, type IssuedCredential, type RoleSummary, type StaffDetail } from '@banking/api';
import { applyFieldErrors, errorMessage, OneTimeCredentialDialog } from '@banking/console';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
  Checkbox,
  DetailList,
  FormField,
  Input,
  Modal,
  PageHeader,
  Select,
  Skeleton,
  StatusBadge,
  Textarea,
  formatDateTime,
  humanize,
} from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useCan, useMe } from '@/lib/me';
import { useBranches, useRoleOptions } from '@/lib/queries';
import { optionalPhone, optionalText, requiredText } from '@/lib/schemas';

export default function StaffMemberPage() {
  const { id } = useParams<{ id: string }>();
  const me = useMe();
  const queryClient = useQueryClient();
  const { nameOf } = useBranches();
  const canEdit = useCan(Permission.staffEdit);
  const canDisable = useCan(Permission.staffDisable);
  const canUnlock = useCan(Permission.staffUnlock);
  const canAssign = useCan(Permission.roleAssign);
  const [dialog, setDialog] = useState<'edit' | 'status' | null>(null);
  const [issued, setIssued] = useState<IssuedCredential | null>(null);

  const staff = useQuery({ queryKey: ['staff-member', id], queryFn: ({ signal }) => bff<StaffDetail>(`/staff/${id}`, { signal }) });
  const reset = useMutation({
    mutationFn: () => bff<IssuedCredential>(`/staff/${id}/credentials/reset`, { method: 'POST' }),
    onSuccess: (credential) => {
      setIssued(credential);
      void queryClient.invalidateQueries({ queryKey: ['staff-member', id] });
    },
  });

  if (staff.isPending) {
    return <Skeleton className="h-64" />;
  }
  if (staff.isError) {
    return <Alert tone="danger">{errorMessage(staff.error)}</Alert>;
  }
  const member = staff.data;
  const isSelf = member.id === me.id;

  return (
    <>
      <PageHeader
        title={`${member.firstName} ${member.lastName}`}
        description={
          <span className="flex items-center gap-2">
            <span>{member.jobTitle ?? 'Staff member'}</span>
            <StatusBadge status={member.status} />
            {isSelf ? <Badge tone="info">You</Badge> : null}
          </span>
        }
        actions={
          <>
            {canEdit && !isSelf ? (
              <Button variant="outline" onClick={() => setDialog('edit')}>
                Edit
              </Button>
            ) : null}
            {canUnlock && !isSelf && member.login && member.status === 'ACTIVE' ? (
              <Button variant="outline" loading={reset.isPending} onClick={() => reset.mutate()}>
                Reset password
              </Button>
            ) : null}
            {canDisable && !isSelf ? (
              <Button variant="outline" onClick={() => setDialog('status')}>
                Change status
              </Button>
            ) : null}
          </>
        }
      />
      {isSelf ? (
        <Alert tone="info" className="mb-4">
          You can't change your own access. Ask another administrator.
        </Alert>
      ) : null}
      {reset.isError ? <Alert tone="danger" className="mb-4">{errorMessage(reset.error)}</Alert> : null}
      {member.statusReason ? (
        <Alert tone="warning" className="mb-4" title={humanize(member.status)}>
          {member.statusReason}
        </Alert>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Profile</CardTitle>
          </CardHeader>
          <CardContent>
            <DetailList
              items={[
                { label: 'Employee number', value: <span className="font-mono">{member.employeeNumber}</span> },
                { label: 'Email', value: member.email },
                { label: 'Phone', value: member.phone },
                { label: 'Home branch', value: nameOf(member.homeBranchId) },
                { label: 'Branch scope', value: member.allBranchesAccess ? 'All branches' : 'Home branch only' },
                { label: 'Created', value: formatDateTime(member.createdAt) },
              ]}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Sign-in</CardTitle>
          </CardHeader>
          <CardContent>
            {member.login ? (
              <DetailList
                items={[
                  { label: 'Username', value: <span className="font-mono">{member.login.username}</span> },
                  { label: 'Sign-in enabled', value: member.login.loginEnabled ? 'Yes' : 'No' },
                  { label: 'Locked', value: member.login.locked ? `Until ${formatDateTime(member.login.lockedUntil)}` : 'No' },
                  { label: 'Must change password', value: member.login.mustChangePassword ? 'Yes' : 'No' },
                  { label: 'Last sign-in', value: formatDateTime(member.login.lastLoginAt) },
                  { label: 'Password changed', value: formatDateTime(member.login.passwordChangedAt) },
                ]}
              />
            ) : (
              <p className="text-sm text-muted-foreground">No sign-in credential.</p>
            )}
          </CardContent>
        </Card>
      </div>

      <RolesCard member={member} editable={canAssign && !isSelf} />

      {dialog === 'edit' ? <EditDialog member={member} onClose={() => setDialog(null)} /> : null}
      {dialog === 'status' ? <StatusDialog member={member} onClose={() => setDialog(null)} /> : null}
      <OneTimeCredentialDialog title="Password reset" credential={issued} onClose={() => setIssued(null)} />
    </>
  );
}

function RolesCard({ member, editable }: { member: StaffDetail; editable: boolean }) {
  const queryClient = useQueryClient();
  const roles = useRoleOptions();
  const [selected, setSelected] = useState<string[]>(member.roles.map((role) => role.id));
  const save = useMutation({
    mutationFn: () => bff<RoleSummary[]>(`/staff/${member.id}/roles`, { method: 'PUT', body: { roleIds: selected } }),
    onSuccess: (assigned) => {
      queryClient.setQueryData<StaffDetail>(['staff-member', member.id], (current) => (current ? { ...current, roles: assigned } : current));
    },
  });
  const changed = selected.length !== member.roles.length || member.roles.some((role) => !selected.includes(role.id));
  const options = (roles.data ?? []).filter((role) => role.status === 'ACTIVE' || selected.includes(role.id));

  return (
    <Card className="mt-6">
      <CardHeader>
        <CardTitle>Roles</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-3">
        {save.isError ? <Alert tone="danger">{errorMessage(save.error)}</Alert> : null}
        {save.isSuccess && !changed ? <Alert tone="success">Roles updated. The change applies at the user's next sign-in or token refresh.</Alert> : null}
        {editable && roles.data ? (
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {options.map((role) => (
              <Checkbox
                key={role.id}
                label={role.name}
                checked={selected.includes(role.id)}
                onChange={(event) => setSelected(event.target.checked ? [...selected, role.id] : selected.filter((idOf) => idOf !== role.id))}
              />
            ))}
          </div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {member.roles.length === 0 ? <span className="text-sm text-muted-foreground">No roles</span> : null}
            {member.roles.map((role) => (
              <Badge key={role.id} tone="info">
                {role.name}
              </Badge>
            ))}
          </div>
        )}
      </CardContent>
      {editable ? (
        <CardFooter>
          <Button variant="outline" disabled={!changed || save.isPending} onClick={() => setSelected(member.roles.map((role) => role.id))}>
            Undo
          </Button>
          <Button disabled={!changed} loading={save.isPending} onClick={() => save.mutate()}>
            Save roles
          </Button>
        </CardFooter>
      ) : null}
    </Card>
  );
}

const editSchema = z.object({
  firstName: requiredText(100),
  lastName: requiredText(100),
  email: z.string().trim().max(254).pipe(z.email('Enter a valid email address')),
  phone: optionalPhone,
  jobTitle: optionalText(100),
  homeBranchId: z.string().uuid(),
  allBranchesAccess: z.boolean(),
});

function EditDialog({ member, onClose }: { member: StaffDetail; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { activeBranches } = useBranches();
  const [failure, setFailure] = useState<string>();
  const form = useForm<z.input<typeof editSchema>, unknown, z.output<typeof editSchema>>({
    resolver: zodResolver(editSchema),
    defaultValues: {
      firstName: member.firstName,
      lastName: member.lastName,
      email: member.email,
      phone: member.phone ?? '',
      jobTitle: member.jobTitle ?? '',
      homeBranchId: member.homeBranchId,
      allBranchesAccess: member.allBranchesAccess,
    },
  });
  const save = useMutation({
    mutationFn: (values: z.output<typeof editSchema>) => bff<StaffDetail>(`/staff/${member.id}`, { method: 'PUT', body: { ...values, version: member.version } }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['staff-member', member.id], updated);
      void queryClient.invalidateQueries({ queryKey: ['staff'] });
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, ['firstName', 'lastName', 'email', 'phone', 'jobTitle', 'homeBranchId'])) {
        setFailure(errorMessage(error));
      }
    },
  });
  const { errors } = form.formState;
  return (
    <Modal open onClose={onClose} busy={save.isPending} title="Edit staff member" className="max-w-2xl">
      <form className="grid gap-4 sm:grid-cols-2" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
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
        <FormField label="Job title" error={errors.jobTitle?.message}>
          {(control) => <Input {...control} {...form.register('jobTitle')} />}
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
        <Checkbox className="sm:col-span-2" label="Access to all branches" {...form.register('allBranchesAccess')} />
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

function StatusDialog({ member, onClose }: { member: StaffDetail; onClose: () => void }) {
  const queryClient = useQueryClient();
  const options = ['ACTIVE', 'SUSPENDED', 'TERMINATED'].filter((status) => status !== member.status);
  const [status, setStatus] = useState(options[0] ?? 'ACTIVE');
  const [reason, setReason] = useState('');
  const change = useMutation({
    mutationFn: () => bff<StaffDetail>(`/staff/${member.id}/status`, { method: 'POST', body: { status, reason: reason.trim(), version: member.version } }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['staff-member', member.id], updated);
      void queryClient.invalidateQueries({ queryKey: ['staff'] });
      onClose();
    },
  });
  return (
    <Modal
      open
      onClose={onClose}
      busy={change.isPending}
      title="Change staff status"
      description="Suspending or terminating ends the member's sessions immediately."
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
