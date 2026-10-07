'use client';

import { bff, Permission, type PermissionInfo, type Role } from '@banking/api';
import { applyFieldErrors, errorMessage } from '@banking/console';
import { Alert, Badge, Button, Checkbox, DataTable, FormField, Input, Modal, PageHeader, Select, StatusBadge, Textarea, humanize } from '@banking/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { useCan } from '@/lib/me';
import { useRoleOptions } from '@/lib/queries';
import { optionalText, requiredText, ROLE_CODE } from '@/lib/schemas';

export default function RolesPage() {
  const roles = useRoleOptions();
  const canManage = useCan(Permission.roleManage);
  const canSeeCatalog = useCan(Permission.permissionView);
  const [editing, setEditing] = useState<Role | 'new' | null>(null);
  const catalog = useQuery({
    queryKey: ['permissions'],
    queryFn: ({ signal }) => bff<PermissionInfo[]>('/permissions', { signal }),
    enabled: canSeeCatalog,
    staleTime: 10 * 60_000,
  });

  const columns: ColumnDef<Role, unknown>[] = [
    {
      header: 'Role',
      cell: ({ row }) => (
        <span className="grid">
          <span className="flex items-center gap-2 font-medium">
            {row.original.name}
            {row.original.systemRole ? <Badge>Template</Badge> : null}
          </span>
          <span className="font-mono text-xs text-muted-foreground">{row.original.code}</span>
        </span>
      ),
    },
    { header: 'Description', cell: ({ row }) => <span className="text-sm text-muted-foreground">{row.original.description ?? '—'}</span> },
    { header: 'Permissions', cell: ({ row }) => row.original.permissions.length },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <>
      <PageHeader
        title="Roles & permissions"
        description="Roles bundle permissions. Sensitive permissions are marked; holders of approval permissions can't approve their own submissions."
        actions={
          canManage && canSeeCatalog ? (
            <Button onClick={() => setEditing('new')}>
              <Plus aria-hidden="true" />
              New role
            </Button>
          ) : null
        }
      />
      {roles.isError ? <Alert tone="danger" className="mb-4">{errorMessage(roles.error)}</Alert> : null}
      <DataTable caption="Roles" columns={columns} data={roles.data} loading={roles.isLoading} onRowClick={setEditing} getRowId={(role) => role.id} emptyTitle="No roles" />
      {editing ? (
        <RoleDialog role={editing === 'new' ? null : editing} catalog={catalog.data} editable={canManage && canSeeCatalog} onClose={() => setEditing(null)} />
      ) : null}
    </>
  );
}

const roleSchema = z.object({
  code: z.string().trim().toUpperCase().regex(ROLE_CODE, 'Use 2-50 upper-case letters, digits or underscores'),
  name: requiredText(100, 'Enter a name'),
  description: optionalText(500),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});

function RoleDialog({ role, catalog, editable, onClose }: { role: Role | null; catalog: PermissionInfo[] | undefined; editable: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [failure, setFailure] = useState<string>();
  const [granted, setGranted] = useState<string[]>(role?.permissions ?? []);
  const form = useForm<z.input<typeof roleSchema>, unknown, z.output<typeof roleSchema>>({
    resolver: zodResolver(roleSchema),
    defaultValues: { code: role?.code ?? '', name: role?.name ?? '', description: role?.description ?? '', status: role?.status ?? 'ACTIVE' },
  });

  const modules = useMemo(() => {
    const grouped = new Map<string, PermissionInfo[]>();
    for (const permission of catalog ?? []) {
      grouped.set(permission.module, [...(grouped.get(permission.module) ?? []), permission]);
    }
    return Array.from(grouped.entries());
  }, [catalog]);

  const save = useMutation({
    mutationFn: (values: z.output<typeof roleSchema>) =>
      role
        ? bff<Role>(`/roles/${role.id}`, {
            method: 'PUT',
            body: { name: values.name, description: values.description, status: values.status, permissions: granted, version: role.version },
          })
        : bff<Role>('/roles', { method: 'POST', body: { code: values.code, name: values.name, description: values.description, permissions: granted } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['roles'] });
      onClose();
    },
    onError: (error) => {
      if (!applyFieldErrors(error, form.setError, ['code', 'name', 'description', 'status'])) {
        setFailure(errorMessage(error));
      }
    },
  });

  function toggle(code: string, on: boolean) {
    setGranted((current) => (on ? [...current, code] : current.filter((existing) => existing !== code)));
  }

  const { errors } = form.formState;
  return (
    <Modal open onClose={onClose} busy={save.isPending} title={role ? role.name : 'New role'} description={role?.systemRole ? 'A default role template provisioned with the institution.' : undefined} className="max-w-4xl">
      <form className="grid max-h-[75vh] gap-4 overflow-y-auto pr-1" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
        {failure ? <Alert tone="danger">{failure}</Alert> : null}
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label="Code" error={errors.code?.message} required>
            {(control) => <Input {...control} disabled={Boolean(role) || !editable} className="uppercase" {...form.register('code')} />}
          </FormField>
          <FormField label="Name" error={errors.name?.message} required>
            {(control) => <Input {...control} disabled={!editable} {...form.register('name')} />}
          </FormField>
          <FormField label="Status">
            {(control) => (
              <Select {...control} disabled={!role || !editable} {...form.register('status')}>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </Select>
            )}
          </FormField>
          <FormField label="Description" error={errors.description?.message} className="sm:col-span-3">
            {(control) => <Textarea {...control} disabled={!editable} {...form.register('description')} />}
          </FormField>
        </div>

        {catalog ? (
          <fieldset className="grid gap-4">
            <legend className="text-sm font-medium">Permissions ({granted.length})</legend>
            {modules.map(([module, permissions]) => (
              <div key={module} className="rounded-md border p-3">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">{humanize(module)}</p>
                <div className="grid gap-2 sm:grid-cols-2">
                  {permissions.map((permission) => (
                    <Checkbox
                      key={permission.code}
                      disabled={!editable}
                      checked={granted.includes(permission.code)}
                      onChange={(event) => toggle(permission.code, event.target.checked)}
                      label={
                        <span className="grid">
                          <span className="flex items-center gap-2">
                            <code className="text-xs">{permission.code}</code>
                            {permission.sensitive ? <Badge tone="warning">Sensitive</Badge> : null}
                          </span>
                          <span className="text-xs text-muted-foreground">{permission.description}</span>
                        </span>
                      }
                    />
                  ))}
                </div>
              </div>
            ))}
          </fieldset>
        ) : (
          <div className="flex flex-wrap gap-1">
            {granted.map((code) => (
              <Badge key={code}>{code}</Badge>
            ))}
          </div>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>
            {editable ? 'Cancel' : 'Close'}
          </Button>
          {editable ? (
            <Button type="submit" loading={save.isPending}>
              {role ? 'Save role' : 'Create role'}
            </Button>
          ) : null}
        </div>
      </form>
    </Modal>
  );
}
