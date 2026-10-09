'use client';

import { bff, query, type AuditSeal, type AuditVerificationReport, type Page } from '@banking/api';
import { Alert, Button, DataTable, FormField, Input, formatDateTime, humanize } from '@banking/ui';
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState, type FormEvent } from 'react';
import { errorMessage } from '../errors';

const columns: ColumnDef<AuditSeal, unknown>[] = [
  { header: 'Seal', cell: ({ row }) => <span className="font-mono">#{row.original.sequenceNo}</span> },
  {
    header: 'Covers',
    cell: ({ row }) => (
      <span className="whitespace-nowrap text-sm">
        {formatDateTime(row.original.rangeStart)} – {formatDateTime(row.original.rangeEnd)}
      </span>
    ),
  },
  { header: 'Entries', cell: ({ row }) => row.original.rowCount },
  { header: 'Seal hash', cell: ({ row }) => <code className="text-xs" title={row.original.sealHash}>{row.original.sealHash.slice(0, 16)}…</code> },
  { header: 'Key', cell: ({ row }) => `v${row.original.keyVersion}` },
];

/** A datetime-local value as an ISO instant (in the browser's time zone), or null when empty. */
function toInstant(value: string): string | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/**
 * The audit trail's seals and an on-demand check of a period. Each seal holds the Merkle root of the entries of its
 * period, chained to the previous seal and signed with a server key, so any entry changed, removed or added
 * afterwards (even directly in the database) is found. `endpoint` is the tenant or the platform seal API.
 */
export function AuditSealsView({ endpoint }: { endpoint: '/audit-seals' | '/platform/audit-seals' }) {
  const [page, setPage] = useState(0);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const seals = useQuery({
    queryKey: ['audit-seals', endpoint, page],
    queryFn: ({ signal }) => bff<Page<AuditSeal>>(`${endpoint}${query({ page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });
  const verify = useMutation({
    mutationFn: () =>
      bff<AuditVerificationReport>(`${endpoint}/verification${query({ from: toInstant(from), to: toInstant(to) })}`),
  });
  const report = verify.data;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    verify.mutate();
  };

  return (
    <div className="grid gap-4">
      <form className="grid gap-3 rounded-lg border bg-card p-4 sm:grid-cols-[1fr_1fr_auto] sm:items-end" onSubmit={submit}>
        <FormField label="Check from" hint="At most 31 days">
          {(control) => <Input {...control} type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} />}
        </FormField>
        <FormField label="To">
          {(control) => <Input {...control} type="datetime-local" value={to} onChange={(event) => setTo(event.target.value)} />}
        </FormField>
        <Button type="submit" disabled={!toInstant(from) || !toInstant(to)} loading={verify.isPending}>
          Verify
        </Button>
      </form>
      {verify.isError ? <Alert tone="danger">{errorMessage(verify.error)}</Alert> : null}
      {report ? (
        report.intact ? (
          <Alert tone="success" title="The audit trail is intact">
            {report.sealsChecked} {report.sealsChecked === 1 ? 'seal' : 'seals'} and {report.rowsChecked} entries checked
            {report.sealedThrough ? `; sealed up to ${formatDateTime(report.sealedThrough)}` : ''}.
            {report.sealsChecked === 0 ? ' No seal covers this period yet.' : ''}
          </Alert>
        ) : (
          <Alert tone="danger" title="The audit trail was changed after it was sealed">
            <ul className="grid gap-1">
              {report.problems.map((problem) => (
                <li key={`${problem.sequenceNo}-${problem.code}`}>
                  Seal #{problem.sequenceNo} ({formatDateTime(problem.rangeStart)} – {formatDateTime(problem.rangeEnd)}): {humanize(problem.code)}.{' '}
                  {problem.detail}
                </li>
              ))}
            </ul>
          </Alert>
        )
      ) : null}
      {seals.isError ? <Alert tone="danger">{errorMessage(seals.error)}</Alert> : null}
      <DataTable
        caption="Audit seals"
        columns={columns}
        data={seals.data?.items}
        loading={seals.isLoading}
        pageInfo={seals.data}
        onPageChange={setPage}
        getRowId={(seal) => seal.id}
        emptyTitle="Nothing sealed yet"
      />
    </div>
  );
}
