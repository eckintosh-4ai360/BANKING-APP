'use client';

import { bff, Permission, query, type CashMovement, type CashMovementType, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, FormField, Input, Modal, Money, Select, StatusBadge, Textarea, formatDateTime, humanize } from '@banking/ui';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useDrawers, useVaults } from '@/components/cash/cash-points-panel';
import { useCan, useMe } from '@/lib/me';
import { useBranches } from '@/lib/queries';
import { AMOUNT } from '@/lib/schemas';

const TYPES: { type: CashMovementType; label: string; hint: string }[] = [
  { type: 'VAULT_TO_DRAWER', label: 'Vault to drawer', hint: 'Fund a till from its branch vault.' },
  { type: 'DRAWER_TO_VAULT', label: 'Drawer to vault', hint: 'Return excess cash from a till to the vault.' },
  { type: 'BANK_TO_VAULT', label: 'Bank to vault', hint: 'Cash withdrawn from the institution’s bank account into a vault.' },
  { type: 'VAULT_TO_BANK', label: 'Vault to bank', hint: 'Cash paid into the institution’s bank account.' },
  { type: 'VAULT_TO_VAULT', label: 'Vault to vault', hint: 'Between branches; the cash is in transit until the receiving branch confirms it.' },
];

type Action = 'approve' | 'reject' | 'receive' | 'cancel';

export function MovementsPanel() {
  const me = useMe();
  const canManage = useCan(Permission.cashManage);
  const canRequest = useCan(Permission.cashManage, Permission.tellerOperate);
  const { nameOf } = useBranches();
  const [status, setStatus] = useState('REQUESTED');
  const [page, setPage] = useState(0);
  const [requesting, setRequesting] = useState(false);
  const [acting, setActing] = useState<{ movement: CashMovement; action: Action } | null>(null);
  const movements = useQuery({
    queryKey: ['cash', 'movements', status, page],
    queryFn: ({ signal }) => bff<Page<CashMovement>>(`/cash/movements${query({ status, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const actionsOf = (movement: CashMovement): Action[] => {
    if (movement.status === 'REQUESTED') {
      const mine = movement.requestedBy === me.id;
      return [...(canManage && !mine ? (['approve', 'reject'] as const) : []), ...(mine ? (['cancel'] as const) : [])];
    }
    return movement.status === 'IN_TRANSIT' && canManage ? ['receive'] : [];
  };

  const columns: ColumnDef<CashMovement, unknown>[] = [
    {
      header: 'Requested',
      cell: ({ row }) => (
        <span className="grid">
          <span>{formatDateTime(row.original.requestedAt)}</span>
          <span className="font-mono text-xs text-muted-foreground">{row.original.reference}</span>
        </span>
      ),
    },
    { header: 'Type', cell: ({ row }) => humanize(row.original.movementType) },
    { header: 'Amount', cell: ({ row }) => <Money amount={row.original.amount} currency={row.original.currency} /> },
    {
      header: 'Branches',
      cell: ({ row }) => (
        <span className="text-sm">
          {nameOf(row.original.fromBranchId ?? row.original.toBranchId)}
          {row.original.toBranchId && row.original.fromBranchId && row.original.toBranchId !== row.original.fromBranchId ? ` → ${nameOf(row.original.toBranchId)}` : ''}
        </span>
      ),
    },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) => (
        <span className="flex justify-end gap-1">
          {actionsOf(row.original).map((action) => (
            <Button key={action} size="sm" variant="ghost" onClick={() => setActing({ movement: row.original, action })}>
              {humanize(action)}
            </Button>
          ))}
        </span>
      ),
    },
  ];

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <FormField label="Status" className="w-48">
          {(control) => (
            <Select {...control} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
              <option value="">Any</option>
              <option value="REQUESTED">Waiting for approval</option>
              <option value="IN_TRANSIT">In transit</option>
              <option value="COMPLETED">Completed</option>
              <option value="REJECTED">Rejected</option>
              <option value="CANCELLED">Cancelled</option>
            </Select>
          )}
        </FormField>
        {canRequest ? (
          <Button onClick={() => setRequesting(true)}>
            <Plus aria-hidden="true" />
            Request movement
          </Button>
        ) : null}
      </div>
      {movements.isError ? <Alert tone="danger">{errorMessage(movements.error)}</Alert> : null}
      <DataTable
        caption="Cash movements"
        columns={columns}
        data={movements.data?.items}
        loading={movements.isLoading}
        pageInfo={movements.data}
        onPageChange={setPage}
        getRowId={(movement) => movement.id}
        emptyTitle={status === 'REQUESTED' ? 'Nothing is waiting for approval' : 'No cash movements'}
      />
      {requesting ? <RequestDialog onClose={() => setRequesting(false)} /> : null}
      {acting ? <ActionDialog {...acting} onClose={() => setActing(null)} /> : null}
    </div>
  );
}

function RequestDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const { activeBranches } = useBranches();
  const vaults = useVaults();
  const drawers = useDrawers();
  const [type, setType] = useState<CashMovementType>('VAULT_TO_DRAWER');
  const [drawerId, setDrawerId] = useState('');
  const [vaultId, setVaultId] = useState('');
  const [toBranchId, setToBranchId] = useState('');
  const [amount, setAmount] = useState('');
  const [note, setNote] = useState('');
  const withDrawer = type === 'VAULT_TO_DRAWER' || type === 'DRAWER_TO_VAULT';
  const drawer = drawers.data?.find((candidate) => candidate.id === drawerId);
  const vault = vaults.data?.find((candidate) => candidate.id === vaultId);
  const currency = withDrawer ? drawer?.currency : vault?.currency;
  const amountValid = AMOUNT.test(amount) && /[1-9]/.test(amount);
  const ready = amountValid && (withDrawer ? !!drawer : !!vault && (type !== 'VAULT_TO_VAULT' || !!toBranchId));

  const request = useMutation({
    mutationFn: () =>
      bff<CashMovement>('/cash/movements', {
        body: {
          movementType: type,
          currency,
          amount,
          ...(withDrawer ? { drawerId } : { branchId: vault?.branchId }),
          ...(type === 'VAULT_TO_VAULT' ? { toBranchId } : {}),
          note: note.trim() || undefined,
        },
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
      busy={request.isPending}
      title="Request a cash movement"
      description="Another person approves it; the cash moves when they do."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={request.isPending}>
            Cancel
          </Button>
          <Button onClick={() => request.mutate()} disabled={!ready} loading={request.isPending}>
            Send for approval
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {request.isError ? <Alert tone="danger">{errorMessage(request.error)}</Alert> : null}
        <FormField label="Movement" required hint={TYPES.find((option) => option.type === type)?.hint}>
          {(control) => (
            <Select {...control} value={type} onChange={(event) => setType(event.target.value as CashMovementType)}>
              {TYPES.map((option) => (
                <option key={option.type} value={option.type}>
                  {option.label}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        {withDrawer ? (
          <FormField label="Drawer" required>
            {(control) => (
              <Select {...control} value={drawerId} onChange={(event) => setDrawerId(event.target.value)}>
                <option value="">Choose a drawer</option>
                {drawers.data?.filter((candidate) => candidate.status === 'ACTIVE').map((candidate) => (
                  <option key={candidate.id} value={candidate.id}>
                    {candidate.code} · {candidate.name} ({candidate.currency})
                  </option>
                ))}
              </Select>
            )}
          </FormField>
        ) : (
          <FormField label={type === 'VAULT_TO_VAULT' ? 'From vault' : 'Vault'} required>
            {(control) => (
              <Select {...control} value={vaultId} onChange={(event) => setVaultId(event.target.value)}>
                <option value="">Choose a vault</option>
                {vaults.data?.filter((candidate) => candidate.status === 'ACTIVE').map((candidate) => (
                  <option key={candidate.id} value={candidate.id}>
                    {candidate.name} ({candidate.currency})
                  </option>
                ))}
              </Select>
            )}
          </FormField>
        )}
        {type === 'VAULT_TO_VAULT' ? (
          <FormField label="To branch" required>
            {(control) => (
              <Select {...control} value={toBranchId} onChange={(event) => setToBranchId(event.target.value)}>
                <option value="">Choose the receiving branch</option>
                {activeBranches.filter((branch) => branch.id !== vault?.branchId).map((branch) => (
                  <option key={branch.id} value={branch.id}>
                    {branch.name} ({branch.code})
                  </option>
                ))}
              </Select>
            )}
          </FormField>
        ) : null}
        <FormField label={`Amount${currency ? ` (${currency})` : ''}`} required error={amount && !amountValid ? 'Enter an amount such as 1250.00' : undefined}>
          {(control) => <Input {...control} inputMode="decimal" autoComplete="off" placeholder="0.00" value={amount} onChange={(event) => setAmount(event.target.value.trim())} />}
        </FormField>
        <FormField label="Note">
          {(control) => <Textarea {...control} maxLength={300} value={note} onChange={(event) => setNote(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}

const ACTION_TEXT: Record<Action, { title: string; confirm: string; description: string }> = {
  approve: { title: 'Approve cash movement', confirm: 'Approve and move the cash', description: 'The cash moves as soon as you approve.' },
  reject: { title: 'Reject cash movement', confirm: 'Reject', description: 'Nothing moves. The requester sees your reason.' },
  receive: { title: 'Confirm cash received', confirm: 'Confirm receipt', description: 'Only confirm once the cash has been counted at your branch.' },
  cancel: { title: 'Cancel your request', confirm: 'Cancel request', description: 'Nothing moves.' },
};

function ActionDialog({ movement, action, onClose }: { movement: CashMovement; action: Action; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const text = ACTION_TEXT[action];
  const act = useMutation({
    mutationFn: () => bff<CashMovement>(`/cash/movements/${movement.id}/${action}`, { body: { note: note.trim(), version: movement.version } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['cash'] });
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={act.isPending}
      title={text.title}
      description={
        <span>
          {movement.reference} · {humanize(movement.movementType)} · <Money amount={movement.amount} currency={movement.currency} />
        </span>
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={act.isPending}>
            Back
          </Button>
          <Button variant={action === 'approve' || action === 'receive' ? 'primary' : 'destructive'} onClick={() => act.mutate()} disabled={!note.trim()} loading={act.isPending}>
            {text.confirm}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        <p className="text-sm text-muted-foreground">{text.description}</p>
        {act.isError ? <Alert tone="danger">{errorMessage(act.error)}</Alert> : null}
        {movement.note ? <p className="text-sm">Request note: {movement.note}</p> : null}
        <FormField label="Note" required>
          {(control) => <Textarea {...control} maxLength={300} value={note} onChange={(event) => setNote(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
