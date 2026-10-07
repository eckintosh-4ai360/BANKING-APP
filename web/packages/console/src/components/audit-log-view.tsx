'use client';

import { bff, query, type AuditLogEntry, type Page } from '@banking/api';
import { Alert, Button, DataTable, DetailList, FormField, Input, Modal, Select, StatusBadge, formatDateTime, humanize } from '@banking/ui';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState, type FormEvent } from 'react';
import { errorMessage } from '../errors';

interface Filters {
  action: string;
  resourceType: string;
  outcome: string;
  from: string;
  to: string;
}

const EMPTY: Filters = { action: '', resourceType: '', outcome: '', from: '', to: '' };

const columns: ColumnDef<AuditLogEntry, unknown>[] = [
  { header: 'When', accessorKey: 'occurredAt', cell: ({ row }) => <span className="whitespace-nowrap">{formatDateTime(row.original.occurredAt)}</span> },
  { header: 'Actor', cell: ({ row }) => row.original.actorName ?? humanize(row.original.actorType) },
  { header: 'Action', accessorKey: 'action', cell: ({ row }) => <code className="text-xs">{row.original.action}</code> },
  {
    header: 'Resource',
    cell: ({ row }) => (
      <span>
        {humanize(row.original.resourceType)}
        {row.original.resourceReference ? <span className="text-muted-foreground"> · {row.original.resourceReference}</span> : null}
      </span>
    ),
  },
  { header: 'Outcome', accessorKey: 'outcome', cell: ({ row }) => <StatusBadge status={row.original.outcome} /> },
  { header: 'IP address', accessorKey: 'ipAddress', cell: ({ row }) => row.original.ipAddress ?? '—' },
];

/** Searchable, read-only audit trail. `endpoint` is the tenant or the platform audit API. */
export function AuditLogView({ endpoint }: { endpoint: '/audit-logs' | '/platform/audit-logs' }) {
  const [draft, setDraft] = useState<Filters>(EMPTY);
  const [filters, setFilters] = useState<Filters>(EMPTY);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<AuditLogEntry | null>(null);

  const result = useQuery({
    queryKey: ['audit', endpoint, filters, page],
    queryFn: ({ signal }) =>
      bff<Page<AuditLogEntry>>(
        `${endpoint}${query({
          action: filters.action.trim(),
          resourceType: filters.resourceType.trim(),
          outcome: filters.outcome,
          from: filters.from ? new Date(`${filters.from}T00:00:00`).toISOString() : undefined,
          to: filters.to ? new Date(`${filters.to}T23:59:59.999`).toISOString() : undefined,
          page,
          size: 25,
        })}`,
        { signal },
      ),
    placeholderData: keepPreviousData,
  });

  function apply(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setFilters(draft);
  }

  return (
    <div className="grid gap-4">
      <form onSubmit={apply} className="grid gap-3 rounded-lg border bg-card p-4 sm:grid-cols-2 lg:grid-cols-6">
        <FormField label="Action" className="lg:col-span-2">
          {(control) => <Input {...control} placeholder="e.g. CUSTOMER_CREATED" value={draft.action} onChange={(event) => setDraft({ ...draft, action: event.target.value })} />}
        </FormField>
        <FormField label="Resource type">
          {(control) => <Input {...control} placeholder="e.g. CUSTOMER" value={draft.resourceType} onChange={(event) => setDraft({ ...draft, resourceType: event.target.value })} />}
        </FormField>
        <FormField label="Outcome">
          {(control) => (
            <Select {...control} value={draft.outcome} onChange={(event) => setDraft({ ...draft, outcome: event.target.value })}>
              <option value="">Any</option>
              <option value="SUCCESS">Success</option>
              <option value="FAILURE">Failure</option>
              <option value="DENIED">Denied</option>
            </Select>
          )}
        </FormField>
        <FormField label="From">
          {(control) => <Input {...control} type="date" value={draft.from} onChange={(event) => setDraft({ ...draft, from: event.target.value })} />}
        </FormField>
        <FormField label="To">
          {(control) => <Input {...control} type="date" value={draft.to} onChange={(event) => setDraft({ ...draft, to: event.target.value })} />}
        </FormField>
        <div className="flex items-end gap-2 lg:col-span-6">
          <Button type="submit">Search</Button>
          <Button
            variant="ghost"
            onClick={() => {
              setDraft(EMPTY);
              setFilters(EMPTY);
              setPage(0);
            }}
          >
            Reset
          </Button>
        </div>
      </form>

      {result.isError ? <Alert tone="danger">{errorMessage(result.error)}</Alert> : null}
      <DataTable
        caption="Audit log"
        columns={columns}
        data={result.data?.items}
        loading={result.isLoading}
        pageInfo={result.data}
        onPageChange={setPage}
        onRowClick={setSelected}
        getRowId={(row) => row.id}
        emptyTitle="No audit events match these filters"
      />

      <Modal open={selected !== null} onClose={() => setSelected(null)} title="Audit event" className="max-w-3xl">
        {selected ? (
          <div className="grid max-h-[70vh] gap-4 overflow-y-auto">
            <DetailList
              items={[
                { label: 'When', value: formatDateTime(selected.occurredAt) },
                { label: 'Outcome', value: <StatusBadge status={selected.outcome} /> },
                { label: 'Actor', value: `${selected.actorName ?? '—'} (${humanize(selected.actorType)})` },
                { label: 'Action', value: <code className="text-xs">{selected.action}</code> },
                { label: 'Resource', value: `${humanize(selected.resourceType)} ${selected.resourceReference ?? selected.resourceId ?? ''}` },
                { label: 'Correlation id', value: <code className="text-xs">{selected.correlationId ?? '—'}</code> },
                { label: 'IP address', value: selected.ipAddress },
                { label: 'User agent', value: <span className="text-xs">{selected.userAgent ?? '—'}</span> },
              ]}
            />
            <JsonBlock label="Before" value={selected.before} />
            <JsonBlock label="After" value={selected.after} />
            <JsonBlock label="Details" value={selected.metadata} />
          </div>
        ) : null}
      </Modal>
    </div>
  );
}

function JsonBlock({ label, value }: { label: string; value: unknown }) {
  if (value === null || value === undefined) {
    return null;
  }
  return (
    <div className="grid gap-1">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <pre className="overflow-x-auto rounded-md bg-muted p-3 text-xs">{JSON.stringify(value, null, 2)}</pre>
    </div>
  );
}
