'use client';

import { bff, Permission, query, type TellerSession } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, DetailList, FormField, Input, Modal, Money, StatusBadge, Textarea, formatDateTime } from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { useCan, useMe } from '@/lib/me';
import { useBranches, useStaffNames } from '@/lib/queries';

/**
 * Teller sessions of a business date in the caller's branches. A count that differs from the ledger waits here for
 * a supervisor (never the teller) to accept it; the difference is then posted to teller shortages or other income.
 */
export function SessionsPanel() {
  const canSupervise = useCan(Permission.tellerSupervise);
  const me = useMe();
  const { nameOf } = useBranches();
  const staffName = useStaffNames();
  const [date, setDate] = useState('');
  const [accepting, setAccepting] = useState<TellerSession | null>(null);
  const sessions = useQuery({
    queryKey: ['teller-sessions', date],
    queryFn: ({ signal }) => bff<TellerSession[]>(`/teller/sessions${query({ date })}`, { signal }),
  });
  const waiting = sessions.data?.filter((session) => session.status === 'BALANCING').length ?? 0;

  const columns: ColumnDef<TellerSession, unknown>[] = [
    {
      header: 'Drawer',
      cell: ({ row }) => (
        <span className="grid">
          <span className="font-mono">{row.original.drawerCode}</span>
          <span className="text-xs text-muted-foreground">{nameOf(row.original.branchId)}</span>
        </span>
      ),
    },
    { header: 'Teller', cell: ({ row }) => staffName(row.original.tellerId) },
    { header: 'Opened', cell: ({ row }) => formatDateTime(row.original.openedAt) },
    { header: 'Opening', cell: ({ row }) => <Money amount={row.original.openingBalance} currency={row.original.currency} /> },
    {
      header: 'Counted',
      cell: ({ row }) => (row.original.countedBalance ? <Money amount={row.original.countedBalance} currency={row.original.currency} /> : '—'),
    },
    {
      header: 'Difference',
      cell: ({ row }) => (row.original.difference ? <Money amount={row.original.difference} currency={row.original.currency} /> : '—'),
    },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) =>
        canSupervise && row.original.status === 'BALANCING' && row.original.tellerId !== me.id ? (
          <Button size="sm" variant="ghost" onClick={() => setAccepting(row.original)}>
            Review difference
          </Button>
        ) : null,
    },
  ];

  return (
    <div className="grid gap-4">
      <FormField label="Business date" hint="Leave empty for today's business date" className="w-56">
        {(control) => <Input {...control} type="date" value={date} onChange={(event) => setDate(event.target.value)} />}
      </FormField>
      {waiting > 0 && canSupervise ? (
        <Alert tone="warning">
          {waiting === 1 ? 'One till is' : `${waiting} tills are`} waiting for a supervisor. End-of-day cannot run until every till is closed.
        </Alert>
      ) : null}
      {sessions.isError ? <Alert tone="danger">{errorMessage(sessions.error)}</Alert> : null}
      <DataTable caption="Teller sessions" columns={columns} data={sessions.data} loading={sessions.isLoading} getRowId={(session) => session.id} emptyTitle="No tills were opened on this date" />
      {accepting ? <AcceptDifferenceDialog session={accepting} onClose={() => setAccepting(null)} /> : null}
    </div>
  );
}

function AcceptDifferenceDialog({ session, onClose }: { session: TellerSession; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const shortage = session.difference?.startsWith('-') ?? false;
  const accept = useMutation({
    mutationFn: () =>
      bff<TellerSession>(`/teller/sessions/${session.id}/accept-difference`, { body: { note: note.trim(), version: session.version } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['teller-sessions'] });
      void queryClient.invalidateQueries({ queryKey: ['cash'] });
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={accept.isPending}
      title={`Accept the ${shortage ? 'shortage' : 'overage'} on ${session.drawerCode}`}
      description={
        shortage
          ? 'The missing cash is posted to teller shortages and the drawer then matches the count.'
          : 'The extra cash is posted to other income and the drawer then matches the count.'
      }
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={accept.isPending}>
            Back
          </Button>
          <Button onClick={() => accept.mutate()} disabled={!note.trim()} loading={accept.isPending}>
            Accept and close the till
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {accept.isError ? <Alert tone="danger">{errorMessage(accept.error)}</Alert> : null}
        <DetailList
          items={[
            { label: 'Expected', value: <Money amount={session.expectedClosingBalance} currency={session.currency} /> },
            { label: 'Counted', value: <Money amount={session.countedBalance} currency={session.currency} /> },
            { label: 'Difference', value: <Money amount={session.difference} currency={session.currency} /> },
            { label: "Teller's note", value: session.closeNote },
          ]}
        />
        <FormField label="Your finding" required hint="Recorded with the decision, e.g. recounted twice">
          {(control) => <Textarea {...control} maxLength={300} value={note} onChange={(event) => setNote(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
