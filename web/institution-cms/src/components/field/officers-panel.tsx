'use client';

import { bff, Permission, query, type FieldDevice, type FieldOfficer, type OfficerCashPosition, type Page, type StaffSummary } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, DetailList, FormField, Input, Modal, Money, Select, StatusBadge, Textarea, formatDateTime } from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { useCan } from '@/lib/me';
import { useBranches } from '@/lib/queries';
import { AMOUNT } from '@/lib/schemas';

export function useFieldOfficers() {
  return useQuery({ queryKey: ['field', 'officers'], queryFn: ({ signal }) => bff<FieldOfficer[]>('/field/officers', { signal }) });
}

export function officerName(officer: FieldOfficer | undefined) {
  return officer ? `${officer.firstName} ${officer.lastName}`.trim() : '—';
}

export function OfficersPanel() {
  const canManage = useCan(Permission.fieldManage);
  const { nameOf } = useBranches();
  const officers = useFieldOfficers();
  const [registering, setRegistering] = useState(false);
  const [selected, setSelected] = useState<FieldOfficer | null>(null);

  const columns: ColumnDef<FieldOfficer, unknown>[] = [
    { header: 'Officer', cell: ({ row }) => <span className="font-medium">{officerName(row.original)}</span> },
    { header: 'Branch', cell: ({ row }) => nameOf(row.original.branchId) },
    { header: 'Cash carried', cell: ({ row }) => <Money amount={row.original.cashBalance} currency={row.original.currency} /> },
    { header: 'Customers', cell: ({ row }) => row.original.assignedCustomers },
    {
      header: 'Open alerts',
      cell: ({ row }) => (row.original.openAlerts > 0 ? <StatusBadge status={`${row.original.openAlerts} OPEN`} /> : '0'),
    },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <div className="grid gap-4">
      {canManage ? (
        <div className="flex justify-end">
          <Button onClick={() => setRegistering(true)}>
            <Plus aria-hidden="true" />
            Register field officer
          </Button>
        </div>
      ) : null}
      {officers.isError ? <Alert tone="danger">{errorMessage(officers.error)}</Alert> : null}
      <DataTable
        caption="Field officers"
        columns={columns}
        data={officers.data}
        loading={officers.isLoading}
        onRowClick={setSelected}
        getRowId={(officer) => officer.staffId}
        emptyTitle="No field officers yet"
      />
      {registering ? <RegisterDialog onClose={() => setRegistering(false)} /> : null}
      {selected ? <OfficerDialog officerId={selected.staffId} onClose={() => setSelected(null)} /> : null}
    </div>
  );
}

function RegisterDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [staffId, setStaffId] = useState('');
  const [maxOfflineAmount, setMaxOfflineAmount] = useState('2000.00');
  const [maxOfflineHours, setMaxOfflineHours] = useState('24');
  const [dailyTarget, setDailyTarget] = useState('');
  const staff = useQuery({
    queryKey: ['staff', 'search', search],
    queryFn: ({ signal }) => bff<Page<StaffSummary>>(`/staff${query({ q: search, status: 'ACTIVE', size: 20 })}`, { signal }),
    enabled: search.trim().length >= 2,
  });
  const hours = Number(maxOfflineHours);
  const valid = !!staffId && AMOUNT.test(maxOfflineAmount) && Number.isInteger(hours) && hours >= 1 && hours <= 720 && (!dailyTarget || AMOUNT.test(dailyTarget));
  const register = useMutation({
    mutationFn: () =>
      bff<FieldOfficer>('/field/officers', {
        body: { staffId, maxOfflineAmount, maxOfflineHours: hours, ...(dailyTarget ? { dailyTarget } : {}) },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['field'] });
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={register.isPending}
      title="Register a field officer"
      description="The officer gets a cash with collectors account: collections raise it, cash handed to a teller lowers it."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={register.isPending}>
            Cancel
          </Button>
          <Button onClick={() => register.mutate()} disabled={!valid} loading={register.isPending}>
            Register
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {register.isError ? <Alert tone="danger">{errorMessage(register.error)}</Alert> : null}
        <FormField label="Find staff member" hint="Type at least two letters of the name">
          {(control) => <Input {...control} value={search} onChange={(event) => setSearch(event.target.value)} />}
        </FormField>
        <FormField label="Staff member" required>
          {(control) => (
            <Select {...control} value={staffId} onChange={(event) => setStaffId(event.target.value)}>
              <option value="">Choose</option>
              {staff.data?.items.map((member) => (
                <option key={member.id} value={member.id}>
                  {member.firstName} {member.lastName} ({member.employeeNumber})
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormField label="Offline cash limit" required hint="Above it, a sync raises an alert">
            {(control) => <Input {...control} inputMode="decimal" value={maxOfflineAmount} onChange={(event) => setMaxOfflineAmount(event.target.value.trim())} />}
          </FormField>
          <FormField label="Offline hours" required hint="Later collections raise an alert">
            {(control) => <Input {...control} inputMode="numeric" value={maxOfflineHours} onChange={(event) => setMaxOfflineHours(event.target.value.trim())} />}
          </FormField>
          <FormField label="Daily target">
            {(control) => <Input {...control} inputMode="decimal" value={dailyTarget} onChange={(event) => setDailyTarget(event.target.value.trim())} />}
          </FormField>
        </div>
      </div>
    </Modal>
  );
}

function OfficerDialog({ officerId, onClose }: { officerId: string; onClose: () => void }) {
  const canManage = useCan(Permission.fieldManage);
  const queryClient = useQueryClient();
  const officer = useQuery({ queryKey: ['field', 'officer', officerId], queryFn: ({ signal }) => bff<FieldOfficer>(`/field/officers/${officerId}`, { signal }) });
  const cash = useQuery({
    queryKey: ['field', 'cash', officerId],
    queryFn: ({ signal }) => bff<OfficerCashPosition>(`/field/officers/${officerId}/cash-position`, { signal }),
  });
  const devices = useQuery({
    queryKey: ['field', 'devices', officerId],
    queryFn: ({ signal }) => bff<FieldDevice[]>(`/field/devices${query({ officerId })}`, { signal }),
  });
  const [reason, setReason] = useState('');
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['field'] });
  const changeStatus = useMutation({
    mutationFn: (status: 'ACTIVE' | 'SUSPENDED') =>
      bff<FieldOfficer>(`/field/officers/${officerId}/status`, { body: { status, reason: reason.trim(), version: officer.data?.version } }),
    onSuccess: () => {
      setReason('');
      refresh();
    },
  });
  const revoke = useMutation({
    mutationFn: (device: FieldDevice) =>
      bff<FieldDevice>(`/field/devices/${device.id}/revoke`, { body: { note: reason.trim(), version: device.version } }),
    onSuccess: () => {
      setReason('');
      refresh();
    },
  });
  const current = officer.data;
  const position = cash.data;

  return (
    <Modal open onClose={onClose} className="max-w-2xl" title={officerName(current)} description={current ? <StatusBadge status={current.status} /> : null} footer={<Button variant="outline" onClick={onClose}>Close</Button>}>
      <div className="grid gap-4">
        {officer.isError ? <Alert tone="danger">{errorMessage(officer.error)}</Alert> : null}
        {position ? (
          <>
            {position.reconciled ? null : (
              <Alert tone="danger" title="The officer's cash does not reconcile">
                The ledger holds <Money amount={position.ledgerBalance} currency={position.currency} /> but collections less remittances come to{' '}
                <Money amount={position.expected} currency={position.currency} />.
              </Alert>
            )}
            <DetailList
              columns={3}
              items={[
                { label: 'Cash carried', value: <Money amount={position.ledgerBalance} currency={position.currency} /> },
                { label: 'Collected today', value: <Money amount={position.collectedToday} currency={position.currency} /> },
                { label: 'Handed to tellers today', value: <Money amount={position.remittedToday} currency={position.currency} /> },
                { label: 'Collected, ever', value: <Money amount={position.collected} currency={position.currency} /> },
                { label: 'Handed over, ever', value: <Money amount={position.remitted} currency={position.currency} /> },
                { label: 'Reconciled', value: position.reconciled ? 'Yes' : 'No' },
              ]}
            />
          </>
        ) : null}
        {current ? (
          <DetailList
            columns={3}
            items={[
              { label: 'Offline cash limit', value: <Money amount={current.maxOfflineAmount} currency={current.currency} /> },
              { label: 'Offline hours', value: current.maxOfflineHours },
              { label: 'Daily target', value: current.dailyTarget ? <Money amount={current.dailyTarget} currency={current.currency} /> : null },
            ]}
          />
        ) : null}
        <section className="grid gap-2">
          <h3 className="text-sm font-semibold">Devices</h3>
          {devices.data?.length === 0 ? <p className="text-sm text-muted-foreground">No phone registered yet.</p> : null}
          {devices.data?.map((device) => (
            <div key={device.id} className="flex items-center justify-between gap-2 rounded-md border p-2 text-sm">
              <span>
                {device.name} · last number {device.lastSequenceNo} · synced {formatDateTime(device.lastSyncedAt)}
              </span>
              {device.live ? (
                canManage ? (
                  <Button size="sm" variant="ghost" disabled={!reason.trim()} onClick={() => revoke.mutate(device)} loading={revoke.isPending && revoke.variables?.id === device.id}>
                    Revoke
                  </Button>
                ) : (
                  <StatusBadge status="ACTIVE" />
                )
              ) : (
                <StatusBadge status="REVOKED" />
              )}
            </div>
          ))}
        </section>
        {canManage && current ? (
          <section className="grid gap-2">
            {changeStatus.isError ? <Alert tone="danger">{errorMessage(changeStatus.error)}</Alert> : null}
            {revoke.isError ? <Alert tone="danger">{errorMessage(revoke.error)}</Alert> : null}
            <FormField label="Reason" hint="Needed to suspend, reinstate or revoke a phone">
              {(control) => <Textarea {...control} maxLength={300} value={reason} onChange={(event) => setReason(event.target.value)} />}
            </FormField>
            <div>
              {current.status === 'ACTIVE' ? (
                <Button variant="destructive" size="sm" disabled={!reason.trim()} onClick={() => changeStatus.mutate('SUSPENDED')} loading={changeStatus.isPending}>
                  Suspend officer
                </Button>
              ) : (
                <Button size="sm" disabled={!reason.trim()} onClick={() => changeStatus.mutate('ACTIVE')} loading={changeStatus.isPending}>
                  Reinstate officer
                </Button>
              )}
            </div>
          </section>
        ) : null}
      </div>
    </Modal>
  );
}
