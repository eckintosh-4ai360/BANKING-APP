'use client';

import { bff, Permission, query, type CustomerAssignment, type CustomerSummary, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, FormField, Input, Modal, Select, Textarea, formatDateTime } from '@banking/ui';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Plus } from 'lucide-react';
import { useState } from 'react';
import { officerName, useFieldOfficers } from '@/components/field/officers-panel';
import { useCan } from '@/lib/me';

export function AssignmentsPanel() {
  const canManage = useCan(Permission.fieldManage);
  const officers = useFieldOfficers();
  const [officerId, setOfficerId] = useState('');
  const [page, setPage] = useState(0);
  const [assigning, setAssigning] = useState(false);
  const [ending, setEnding] = useState<CustomerAssignment | null>(null);
  const assignments = useQuery({
    queryKey: ['field', 'assignments', officerId, page],
    queryFn: ({ signal }) => bff<Page<CustomerAssignment>>(`/field/assignments${query({ officerId, page, size: 20 })}`, { signal }),
    placeholderData: keepPreviousData,
  });
  const nameOf = (id: string) => officerName(officers.data?.find((officer) => officer.staffId === id));

  const columns: ColumnDef<CustomerAssignment, unknown>[] = [
    {
      header: 'Customer',
      cell: ({ row }) => (
        <span className="grid">
          <span className="font-medium">{row.original.customerName ?? '—'}</span>
          <span className="font-mono text-xs text-muted-foreground">{row.original.customerNumber}</span>
        </span>
      ),
    },
    { header: 'Field officer', cell: ({ row }) => nameOf(row.original.officerId) },
    { header: 'Since', cell: ({ row }) => formatDateTime(row.original.assignedAt) },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) =>
        canManage ? (
          <span className="flex justify-end">
            <Button size="sm" variant="ghost" onClick={() => setEnding(row.original)}>
              End
            </Button>
          </span>
        ) : null,
    },
  ];

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <FormField label="Field officer" className="w-64">
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
        {canManage ? (
          <Button onClick={() => setAssigning(true)}>
            <Plus aria-hidden="true" />
            Assign customer
          </Button>
        ) : null}
      </div>
      {assignments.isError ? <Alert tone="danger">{errorMessage(assignments.error)}</Alert> : null}
      <DataTable
        caption="Customer assignments"
        columns={columns}
        data={assignments.data?.items}
        loading={assignments.isLoading}
        pageInfo={assignments.data}
        onPageChange={setPage}
        getRowId={(assignment) => assignment.id}
        emptyTitle="No customers assigned"
      />
      {assigning ? <AssignDialog onClose={() => setAssigning(false)} /> : null}
      {ending ? <EndDialog assignment={ending} onClose={() => setEnding(null)} /> : null}
    </div>
  );
}

function AssignDialog({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const officers = useFieldOfficers();
  const [search, setSearch] = useState('');
  const [customerId, setCustomerId] = useState('');
  const [officerId, setOfficerId] = useState('');
  const customers = useQuery({
    queryKey: ['customers', 'search', search],
    queryFn: ({ signal }) => bff<Page<CustomerSummary>>(`/customers${query({ q: search, size: 20 })}`, { signal }),
    enabled: search.trim().length >= 2,
  });
  const assign = useMutation({
    mutationFn: () => bff<CustomerAssignment>('/field/assignments', { body: { customerId, officerId } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['field'] });
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={assign.isPending}
      title="Assign a customer"
      description="The officer collects from the customer in the field. A customer already assigned moves to the new officer."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={assign.isPending}>
            Cancel
          </Button>
          <Button onClick={() => assign.mutate()} disabled={!customerId || !officerId} loading={assign.isPending}>
            Assign
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {assign.isError ? <Alert tone="danger">{errorMessage(assign.error)}</Alert> : null}
        <FormField label="Find customer" hint="Name, customer number or phone">
          {(control) => <Input {...control} value={search} onChange={(event) => setSearch(event.target.value)} />}
        </FormField>
        <FormField label="Customer" required>
          {(control) => (
            <Select {...control} value={customerId} onChange={(event) => setCustomerId(event.target.value)}>
              <option value="">Choose</option>
              {customers.data?.items.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.displayName} ({customer.customerNumber})
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="Field officer" required>
          {(control) => (
            <Select {...control} value={officerId} onChange={(event) => setOfficerId(event.target.value)}>
              <option value="">Choose</option>
              {officers.data?.filter((officer) => officer.status === 'ACTIVE').map((officer) => (
                <option key={officer.staffId} value={officer.staffId}>
                  {officerName(officer)}
                </option>
              ))}
            </Select>
          )}
        </FormField>
      </div>
    </Modal>
  );
}

function EndDialog({ assignment, onClose }: { assignment: CustomerAssignment; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const end = useMutation({
    mutationFn: () => bff<CustomerAssignment>(`/field/assignments/${assignment.id}/end`, { body: { reason: reason.trim() } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['field'] });
      onClose();
    },
  });
  return (
    <Modal
      open
      onClose={onClose}
      busy={end.isPending}
      title={`Stop field collection from ${assignment.customerName ?? 'this customer'}`}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={end.isPending}>
            Back
          </Button>
          <Button variant="destructive" onClick={() => end.mutate()} disabled={!reason.trim()} loading={end.isPending}>
            End assignment
          </Button>
        </>
      }
    >
      <div className="grid gap-3">
        {end.isError ? <Alert tone="danger">{errorMessage(end.error)}</Alert> : null}
        <FormField label="Reason" required>
          {(control) => <Textarea {...control} maxLength={300} value={reason} onChange={(event) => setReason(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
