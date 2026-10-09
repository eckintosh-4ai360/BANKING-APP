'use client';

import { bff, Permission, query, type FieldAlert, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, FormField, Modal, Select, StatusBadge, Textarea, formatDateTime, humanize } from '@banking/ui';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { officerName, useFieldOfficers } from '@/components/field/officers-panel';
import { useCan } from '@/lib/me';

const EXPLAIN: Record<string, string> = {
  SEQUENCE_GAP: 'Numbered collections from a phone never arrived. It closes by itself when they do.',
  CONFLICT: 'A different collection or visit came in under a reference or number already used.',
  LATE_SYNC: 'Collections reached the server later than the officer’s offline time limit.',
  OFFLINE_LIMIT: 'One sync brought more cash than the officer may hold offline.',
};

export function AlertsPanel() {
  const canManage = useCan(Permission.fieldManage);
  const officers = useFieldOfficers();
  const [status, setStatus] = useState('OPEN');
  const [page, setPage] = useState(0);
  const [resolving, setResolving] = useState<FieldAlert | null>(null);
  const alerts = useQuery({
    queryKey: ['field', 'alerts', status, page],
    queryFn: ({ signal }) => bff<Page<FieldAlert>>(`/field/alerts${query({ status, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });

  const columns: ColumnDef<FieldAlert, unknown>[] = [
    { header: 'Raised', cell: ({ row }) => formatDateTime(row.original.raisedAt) },
    { header: 'Officer', cell: ({ row }) => officerName(officers.data?.find((officer) => officer.staffId === row.original.officerId)) },
    {
      header: 'Alert',
      cell: ({ row }) => (
        <span className="grid">
          <span className="font-medium">{humanize(row.original.alertType)}</span>
          <span className="text-sm">{row.original.detail}</span>
        </span>
      ),
    },
    {
      header: 'Status',
      cell: ({ row }) => (
        <span className="grid gap-1">
          <StatusBadge status={row.original.status} />
          {row.original.resolution ? <span className="text-xs text-muted-foreground">{row.original.resolution}</span> : null}
        </span>
      ),
    },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) =>
        canManage && row.original.status === 'OPEN' ? (
          <Button size="sm" variant="ghost" onClick={() => setResolving(row.original)}>
            Resolve
          </Button>
        ) : null,
    },
  ];

  return (
    <div className="grid gap-4">
      <FormField label="Status" className="w-48">
        {(control) => (
          <Select {...control} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
            <option value="OPEN">Open</option>
            <option value="RESOLVED">Resolved</option>
            <option value="">Any</option>
          </Select>
        )}
      </FormField>
      {alerts.isError ? <Alert tone="danger">{errorMessage(alerts.error)}</Alert> : null}
      <DataTable caption="Field alerts" columns={columns} data={alerts.data?.items} loading={alerts.isLoading} pageInfo={alerts.data} onPageChange={setPage} getRowId={(alert) => alert.id} emptyTitle={status === 'OPEN' ? 'No open alerts' : 'No alerts'} />
      {resolving ? <ResolveDialog alert={resolving} onClose={() => setResolving(null)} /> : null}
    </div>
  );
}

function ResolveDialog({ alert, onClose }: { alert: FieldAlert; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const resolve = useMutation({
    mutationFn: () => bff<FieldAlert>(`/field/alerts/${alert.id}/resolve`, { body: { note: note.trim(), version: alert.version } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['field'] });
      onClose();
    },
  });
  return (
    <Modal
      open
      onClose={onClose}
      busy={resolve.isPending}
      title={`Resolve: ${humanize(alert.alertType)}`}
      description={EXPLAIN[alert.alertType]}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={resolve.isPending}>
            Back
          </Button>
          <Button onClick={() => resolve.mutate()} disabled={!note.trim()} loading={resolve.isPending}>
            Resolve
          </Button>
        </>
      }
    >
      <div className="grid gap-3">
        <p className="text-sm">{alert.detail}</p>
        {resolve.isError ? <Alert tone="danger">{errorMessage(resolve.error)}</Alert> : null}
        <FormField label="What you found" required>
          {(control) => <Textarea {...control} maxLength={300} value={note} onChange={(event) => setNote(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
