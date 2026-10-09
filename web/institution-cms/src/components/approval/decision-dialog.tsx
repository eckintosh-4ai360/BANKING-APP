'use client';

import { bff, Permission, type Approval } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DetailList, FormField, Modal, Money, StatusBadge, Textarea, formatDateTime, humanize } from '@banking/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useCan, useMe } from '@/lib/me';

/** Shows the request and lets a checker approve or reject it. The server enforces four eyes and permissions. */
export function DecisionDialog({ approval, onClose }: { approval: Approval; onClose: () => void }) {
  const me = useMe();
  const queryClient = useQueryClient();
  const canAct = useCan(Permission.approvalAct);
  const [note, setNote] = useState('');
  const ownRequest = approval.requestedBy === me.id;
  const decide = useMutation({
    mutationFn: (decision: 'approve' | 'reject') =>
      bff<Approval>(`/approvals/${approval.id}/${decision}`, { body: { note: note.trim() || undefined, version: approval.version } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['approvals'] });
      void queryClient.invalidateQueries({ queryKey: ['account'] });
      void queryClient.invalidateQueries({ queryKey: ['account-transactions'] });
      onClose();
    },
  });
  const pending = approval.status === 'PENDING';
  return (
    <Modal
      open
      onClose={onClose}
      busy={decide.isPending}
      title={humanize(approval.requestType)}
      description={approval.summary}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={decide.isPending}>
            Close
          </Button>
          {pending && canAct && !ownRequest ? (
            <>
              <Button
                variant="destructive"
                loading={decide.isPending && decide.variables === 'reject'}
                disabled={note.trim() === '' || decide.isPending}
                onClick={() => decide.mutate('reject')}
              >
                Reject
              </Button>
              <Button loading={decide.isPending && decide.variables === 'approve'} disabled={decide.isPending} onClick={() => decide.mutate('approve')}>
                Approve
              </Button>
            </>
          ) : null}
        </>
      }
    >
      <div className="grid gap-4">
        {decide.isError ? <Alert tone="danger">{errorMessage(decide.error)}</Alert> : null}
        {pending && ownRequest ? (
          <Alert tone="info" title="Four-eyes rule">
            You made this request, so someone else must decide it.
          </Alert>
        ) : null}
        <DetailList
          items={[
            { label: 'Status', value: <StatusBadge status={approval.status} /> },
            { label: 'Amount', value: approval.amount && approval.currency ? <Money amount={approval.amount} currency={approval.currency} /> : null },
            { label: 'Requested', value: formatDateTime(approval.requestedAt) },
            { label: 'Decided', value: formatDateTime(approval.decidedAt) },
            { label: 'Decision note', value: approval.decisionNote },
          ]}
        />
        {approval.requestType === 'MANUAL_JOURNAL' ? <JournalLines payload={approval.payload} /> : null}
        {pending && canAct && !ownRequest ? (
          <FormField label="Note" hint="Required when rejecting">
            {(control) => <Textarea {...control} value={note} maxLength={300} onChange={(event) => setNote(event.target.value)} />}
          </FormField>
        ) : null}
      </div>
    </Modal>
  );
}

interface JournalPayload {
  description?: string;
  lines?: { chartOfAccountId: string; currency: string; direction: string; amount: string; narration?: string }[];
}

function JournalLines({ payload }: { payload: unknown }) {
  const journal = (payload ?? {}) as JournalPayload;
  return (
    <table className="w-full text-sm" aria-label="Journal lines">
      <thead className="text-left text-xs uppercase text-muted-foreground">
        <tr>
          <th className="py-1 pr-2">GL account</th>
          <th className="py-1 pr-2 text-right">Debit</th>
          <th className="py-1 text-right">Credit</th>
        </tr>
      </thead>
      <tbody className="divide-y">
        {(journal.lines ?? []).map((line, index) => (
          <tr key={index}>
            <td className="py-1 pr-2 font-mono text-xs">{line.chartOfAccountId}</td>
            <td className="py-1 pr-2 text-right">{line.direction === 'DEBIT' ? <Money amount={line.amount} currency={line.currency} /> : null}</td>
            <td className="py-1 text-right">{line.direction === 'CREDIT' ? <Money amount={line.amount} currency={line.currency} /> : null}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
