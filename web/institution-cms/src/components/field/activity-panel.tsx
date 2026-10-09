'use client';

import { bff, query, type CustomerVisit, type FieldCollection, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, DataTable, FormField, Money, Select, StatusBadge, formatDateTime, humanize } from '@banking/ui';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { officerName, useFieldOfficers } from '@/components/field/officers-panel';

/**
 * What arrived from the field: collections (posted, or rejected with the reason; a rejected collection's cash is
 * still with the officer) and visits.
 */
export function ActivityPanel({ kind }: { kind: 'collections' | 'visits' }) {
  const officers = useFieldOfficers();
  const [officerId, setOfficerId] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const nameOf = (id: string) => officerName(officers.data?.find((officer) => officer.staffId === id));

  return (
    <div className="grid gap-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <FormField label="Field officer">
          {(control) => (
            <Select {...control} value={officerId} onChange={(event) => { setOfficerId(event.target.value); setPage(0); }}>
              <option value="">All</option>
              {officers.data?.map((officer) => (
                <option key={officer.staffId} value={officer.staffId}>
                  {officerName(officer)}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        {kind === 'collections' ? (
          <FormField label="Outcome">
            {(control) => (
              <Select {...control} value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }}>
                <option value="">Any</option>
                <option value="POSTED">Posted</option>
                <option value="REJECTED">Rejected</option>
              </Select>
            )}
          </FormField>
        ) : null}
      </div>
      {kind === 'collections' ? (
        <CollectionsTable officerId={officerId} status={status} page={page} onPageChange={setPage} nameOf={nameOf} />
      ) : (
        <VisitsTable officerId={officerId} page={page} onPageChange={setPage} nameOf={nameOf} />
      )}
    </div>
  );
}

function CollectionsTable({ officerId, status, page, onPageChange, nameOf }: { officerId: string; status: string; page: number; onPageChange: (page: number) => void; nameOf: (id: string) => string }) {
  const collections = useQuery({
    queryKey: ['field', 'collections', officerId, status, page],
    queryFn: ({ signal }) => bff<Page<FieldCollection>>(`/field/collections${query({ officerId, status, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });
  const columns: ColumnDef<FieldCollection, unknown>[] = [
    {
      header: 'Collected',
      cell: ({ row }) => (
        <span className="grid">
          <span>{formatDateTime(row.original.collectedAt)}</span>
          <span className="text-xs text-muted-foreground">received {formatDateTime(row.original.receivedAt)}</span>
        </span>
      ),
    },
    { header: 'Officer', cell: ({ row }) => nameOf(row.original.officerId) },
    { header: 'For', cell: ({ row }) => humanize(row.original.targetType) },
    { header: 'Amount', cell: ({ row }) => <Money amount={row.original.amount} currency={row.original.currency} /> },
    { header: 'Device no.', cell: ({ row }) => <span className="font-mono">{row.original.sequenceNo}</span> },
    {
      header: 'Outcome',
      cell: ({ row }) => (
        <span className="grid gap-1">
          <StatusBadge status={row.original.status} />
          {row.original.rejectionReason ? <span className="text-xs text-destructive">{row.original.rejectionReason}</span> : null}
        </span>
      ),
    },
  ];
  return (
    <>
      {collections.isError ? <Alert tone="danger">{errorMessage(collections.error)}</Alert> : null}
      <DataTable caption="Field collections" columns={columns} data={collections.data?.items} loading={collections.isLoading} pageInfo={collections.data} onPageChange={onPageChange} getRowId={(collection) => collection.id} emptyTitle="Nothing collected yet" />
    </>
  );
}

function VisitsTable({ officerId, page, onPageChange, nameOf }: { officerId: string; page: number; onPageChange: (page: number) => void; nameOf: (id: string) => string }) {
  const visits = useQuery({
    queryKey: ['field', 'visits', officerId, page],
    queryFn: ({ signal }) => bff<Page<CustomerVisit>>(`/field/visits${query({ officerId, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });
  const columns: ColumnDef<CustomerVisit, unknown>[] = [
    { header: 'Visited', cell: ({ row }) => formatDateTime(row.original.visitedAt) },
    { header: 'Officer', cell: ({ row }) => nameOf(row.original.officerId) },
    { header: 'Purpose', cell: ({ row }) => humanize(row.original.purpose) },
    { header: 'Outcome', cell: ({ row }) => humanize(row.original.outcome) },
    { header: 'Notes', cell: ({ row }) => <span className="text-sm">{row.original.notes ?? '—'}</span> },
  ];
  return (
    <>
      {visits.isError ? <Alert tone="danger">{errorMessage(visits.error)}</Alert> : null}
      <DataTable caption="Customer visits" columns={columns} data={visits.data?.items} loading={visits.isLoading} pageInfo={visits.data} onPageChange={onPageChange} getRowId={(visit) => visit.id} emptyTitle="No visits recorded" />
    </>
  );
}
