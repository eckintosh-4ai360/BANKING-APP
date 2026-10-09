'use client';

import { ApiError, bff, query, type Drawer, type TellerSession } from '@banking/api';
import { errorMessage } from '@banking/console';
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  DetailList,
  FormField,
  Modal,
  Money,
  PageHeader,
  Select,
  Skeleton,
  StatCard,
  StatusBadge,
  Textarea,
  formatDate,
  formatDateTime,
} from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Link from 'next/link';
import { useState } from 'react';
import { CountFields } from '@/components/cash/count-fields';
import { isValidCount, toCashCount, type CountInput } from '@/lib/cash';
import { useMe } from '@/lib/me';

/**
 * The signed-in teller's till: open it on a drawer, see the cash it should hold, and close it with a blind count.
 * Cash deposits and withdrawals (on the account screens) post to this drawer while it is open.
 */
export default function TellerPage() {
  const session = useQuery({
    queryKey: ['teller-session', 'me'],
    queryFn: async ({ signal }) => {
      try {
        return await bff<TellerSession>('/teller/sessions/me', { signal });
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
  });

  return (
    <>
      <PageHeader
        title="My till"
        description="Open your drawer before taking cash, count it note by note when you finish. The count is compared with the ledger only after you submit it."
      />
      {session.isError ? <Alert tone="danger">{errorMessage(session.error)}</Alert> : null}
      {session.isPending ? <Skeleton className="h-48" /> : null}
      {session.data === null ? <OpenTill /> : null}
      {session.data ? <OpenSession session={session.data} /> : null}
    </>
  );
}

function OpenTill() {
  const me = useMe();
  const queryClient = useQueryClient();
  const [drawerId, setDrawerId] = useState('');
  const [count, setCount] = useState<CountInput>({});
  const drawers = useQuery({
    queryKey: ['cash', 'drawers', 'free'],
    queryFn: ({ signal }) => bff<Drawer[]>(`/cash/drawers${query({ branchId: me.homeBranchId })}`, { signal }),
    select: (all) => all.filter((drawer) => drawer.status === 'ACTIVE' && !drawer.sessionId),
  });
  const drawer = drawers.data?.find((candidate) => candidate.id === drawerId);
  const open = useMutation({
    mutationFn: () => {
      const counted = toCashCount(count);
      return bff<TellerSession>('/teller/sessions', {
        body: { drawerId, ...(Object.keys(counted.denominations).length > 0 ? { openingCount: counted } : {}) },
      });
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['teller-session'] }),
  });

  return (
    <Card className="max-w-3xl">
      <CardHeader>
        <CardTitle>Open a till</CardTitle>
        <CardDescription>Choose a free drawer in your branch. If it holds cash, count it: the count must match the ledger.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {drawers.isError ? <Alert tone="danger">{errorMessage(drawers.error)}</Alert> : null}
        {open.isError ? <Alert tone="danger">{errorMessage(open.error)}</Alert> : null}
        {drawers.data && drawers.data.length === 0 ? (
          <Alert tone="info">No free drawer in your branch. A cash manager creates drawers on the Cash screen.</Alert>
        ) : null}
        <FormField label="Drawer" required>
          {(control) => (
            <Select {...control} value={drawerId} onChange={(event) => setDrawerId(event.target.value)} disabled={!drawers.data?.length}>
              <option value="">Choose a drawer</option>
              {drawers.data?.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.code} · {candidate.name} ({candidate.currency})
                </option>
              ))}
            </Select>
          )}
        </FormField>
        {drawer ? <CountFields idPrefix="opening" currency={drawer.currency} value={count} onChange={setCount} /> : null}
        <div>
          <Button onClick={() => open.mutate()} disabled={!drawer || !isValidCount(count)} loading={open.isPending}>
            Open till
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

function OpenSession({ session }: { session: TellerSession }) {
  const [closing, setClosing] = useState(false);
  const money = (amount: string | null) => (amount === null ? '—' : <Money amount={amount} currency={session.currency} />);

  return (
    <div className="grid gap-4">
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label="Cash in the drawer (ledger)" value={money(session.currentBalance)} hint={`Drawer ${session.drawerCode}`} />
        <StatCard label="Opening cash" value={money(session.openingBalance)} hint={`Opened ${formatDateTime(session.openedAt)}`} />
        <StatCard label="Business date" value={formatDate(session.businessDate)} hint={<StatusBadge status={session.status} />} />
      </div>
      {session.status === 'OPEN' ? (
        <Card>
          <CardContent className="flex flex-wrap items-center justify-between gap-3 pt-6">
            <p className="text-sm text-muted-foreground">
              Take deposits and pay withdrawals from the <Link className="underline" href="/accounts">account screens</Link>. When you finish, close
              the till with a full count.
            </p>
            <Button onClick={() => setClosing(true)}>Count and close</Button>
          </CardContent>
        </Card>
      ) : (
        <Alert tone="warning" title="Waiting for a supervisor">
          Your count of {money(session.countedBalance)} differs from the expected {money(session.expectedClosingBalance)} by{' '}
          {money(session.difference)}. A supervisor must accept the difference before the till closes; no cash can be taken meanwhile.
        </Alert>
      )}
      {closing ? <CloseDialog session={session} onClose={() => setClosing(false)} /> : null}
    </div>
  );
}

function CloseDialog({ session, onClose }: { session: TellerSession; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [count, setCount] = useState<CountInput>({});
  const [note, setNote] = useState('');
  const [result, setResult] = useState<TellerSession | null>(null);
  const close = useMutation({
    mutationFn: () =>
      bff<TellerSession>(`/teller/sessions/${session.id}/close`, {
        body: { count: toCashCount(count), note: note.trim() || undefined, version: session.version },
      }),
    onSuccess: (closed) => {
      setResult(closed);
      void queryClient.invalidateQueries({ queryKey: ['teller-session'] });
    },
  });

  if (result) {
    return (
      <Modal open onClose={onClose} title={result.status === 'CLOSED' ? 'Till closed' : 'Count recorded'} footer={<Button onClick={onClose}>Done</Button>}>
        {result.status === 'CLOSED' ? (
          <Alert tone="success">The count matches the ledger exactly. Your till is closed.</Alert>
        ) : (
          <Alert tone="warning">The count differs from the ledger. A supervisor will review it.</Alert>
        )}
        <DetailList
          items={[
            { label: 'Expected', value: <Money amount={result.expectedClosingBalance} currency={result.currency} /> },
            { label: 'Counted', value: <Money amount={result.countedBalance} currency={result.currency} /> },
            { label: 'Difference', value: <Money amount={result.difference} currency={result.currency} /> },
          ]}
        />
      </Modal>
    );
  }

  return (
    <Modal
      open
      onClose={onClose}
      busy={close.isPending}
      title={`Close till ${session.drawerCode}`}
      description="Count every note and coin in the drawer. The expected amount is only compared after you submit."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={close.isPending}>
            Cancel
          </Button>
          <Button onClick={() => close.mutate()} disabled={!isValidCount(count)} loading={close.isPending}>
            Submit count
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {close.isError ? <Alert tone="danger">{errorMessage(close.error)}</Alert> : null}
        <CountFields idPrefix="closing" currency={session.currency} value={count} onChange={setCount} />
        <FormField label="Note" hint="Optional, e.g. why the count may differ">
          {(control) => <Textarea {...control} maxLength={300} value={note} onChange={(event) => setNote(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
