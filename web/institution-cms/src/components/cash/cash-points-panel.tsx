'use client';

import { bff, Permission, type Drawer, type Vault } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, FormField, Input, Modal, Money, Select, StatusBadge } from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useCan } from '@/lib/me';
import { useBranches } from '@/lib/queries';

const DRAWER_CODE = /^[A-Z0-9][A-Z0-9-]{0,19}$/;

export function useVaults() {
  return useQuery({ queryKey: ['cash', 'vaults'], queryFn: ({ signal }) => bff<Vault[]>('/cash/vaults', { signal }) });
}

export function useDrawers() {
  return useQuery({ queryKey: ['cash', 'drawers'], queryFn: ({ signal }) => bff<Drawer[]>('/cash/drawers', { signal }) });
}

export function CashPointsPanel() {
  const canManage = useCan(Permission.cashManage);
  const { nameOf } = useBranches();
  const vaults = useVaults();
  const drawers = useDrawers();
  const [creating, setCreating] = useState<'vault' | 'drawer' | null>(null);

  const vaultColumns: ColumnDef<Vault, unknown>[] = [
    { header: 'Vault', cell: ({ row }) => <span className="font-medium">{row.original.name}</span> },
    { header: 'Branch', cell: ({ row }) => nameOf(row.original.branchId) },
    { header: 'Cash', cell: ({ row }) => <Money amount={row.original.balance} currency={row.original.currency} /> },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];
  const drawerColumns: ColumnDef<Drawer, unknown>[] = [
    {
      header: 'Drawer',
      cell: ({ row }) => (
        <span className="grid">
          <span className="font-medium">{row.original.name}</span>
          <span className="font-mono text-xs text-muted-foreground">{row.original.code}</span>
        </span>
      ),
    },
    { header: 'Branch', cell: ({ row }) => nameOf(row.original.branchId) },
    { header: 'Cash', cell: ({ row }) => <Money amount={row.original.balance} currency={row.original.currency} /> },
    { header: 'Till', cell: ({ row }) => (row.original.sessionId ? <StatusBadge status="OPEN" /> : <span className="text-sm text-muted-foreground">Free</span>) },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <div className="grid gap-6">
      <section className="grid gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Vaults</h2>
          {canManage ? (
            <Button size="sm" onClick={() => setCreating('vault')}>
              <Plus aria-hidden="true" />
              New vault
            </Button>
          ) : null}
        </div>
        {vaults.isError ? <Alert tone="danger">{errorMessage(vaults.error)}</Alert> : null}
        <DataTable caption="Vaults" columns={vaultColumns} data={vaults.data} loading={vaults.isLoading} getRowId={(vault) => vault.id} emptyTitle="No vaults yet" />
      </section>
      <section className="grid gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Teller drawers</h2>
          {canManage ? (
            <Button size="sm" onClick={() => setCreating('drawer')}>
              <Plus aria-hidden="true" />
              New drawer
            </Button>
          ) : null}
        </div>
        {drawers.isError ? <Alert tone="danger">{errorMessage(drawers.error)}</Alert> : null}
        <DataTable caption="Teller drawers" columns={drawerColumns} data={drawers.data} loading={drawers.isLoading} getRowId={(drawer) => drawer.id} emptyTitle="No drawers yet" />
      </section>
      {creating ? <CashPointDialog kind={creating} onClose={() => setCreating(null)} /> : null}
    </div>
  );
}

function CashPointDialog({ kind, onClose }: { kind: 'vault' | 'drawer'; onClose: () => void }) {
  const queryClient = useQueryClient();
  const { activeBranches } = useBranches();
  const [branchId, setBranchId] = useState('');
  const [currency, setCurrency] = useState('GHS');
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const codeValid = kind === 'vault' || DRAWER_CODE.test(code);
  const create = useMutation({
    mutationFn: () =>
      bff(kind === 'vault' ? '/cash/vaults' : '/cash/drawers', {
        body: { branchId, currency, name: name.trim(), ...(kind === 'drawer' ? { code } : {}) },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['cash'] });
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={create.isPending}
      title={kind === 'vault' ? 'New vault' : 'New teller drawer'}
      description={kind === 'vault' ? 'One active vault per branch and currency.' : 'A till that one teller at a time opens for the day.'}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={create.isPending}>
            Cancel
          </Button>
          <Button onClick={() => create.mutate()} disabled={!branchId || !name.trim() || !codeValid} loading={create.isPending}>
            Create
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {create.isError ? <Alert tone="danger">{errorMessage(create.error)}</Alert> : null}
        <FormField label="Branch" required>
          {(control) => (
            <Select {...control} value={branchId} onChange={(event) => setBranchId(event.target.value)}>
              <option value="">Choose a branch</option>
              {activeBranches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name} ({branch.code})
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="Currency" required>
          {(control) => <Input {...control} maxLength={3} value={currency} onChange={(event) => setCurrency(event.target.value.toUpperCase())} />}
        </FormField>
        {kind === 'drawer' ? (
          <FormField label="Code" required hint="Upper-case letters, digits or hyphens, e.g. T1" error={code && !codeValid ? 'Use up to 20 upper-case letters, digits or hyphens' : undefined}>
            {(control) => <Input {...control} maxLength={20} value={code} onChange={(event) => setCode(event.target.value.toUpperCase())} />}
          </FormField>
        ) : null}
        <FormField label="Name" required>
          {(control) => <Input {...control} maxLength={100} value={name} onChange={(event) => setName(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
