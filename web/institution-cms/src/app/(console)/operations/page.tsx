'use client';

import { bff, Permission, query, type BusinessDate, type EodRun, type Holiday, type Page } from '@banking/api';
import { errorMessage } from '@banking/console';
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Checkbox,
  DataTable,
  FormField,
  Input,
  Modal,
  PageHeader,
  Select,
  StatCard,
  StatusBadge,
  formatDate,
  formatDateTime,
  humanize,
} from '@banking/ui';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { useCan } from '@/lib/me';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

function useBusinessDate() {
  return useQuery({ queryKey: ['business-date'], queryFn: ({ signal }) => bff<BusinessDate>('/operations/business-date', { signal }) });
}

export default function OperationsPage() {
  const canManage = useCan(Permission.operationsManage);
  const businessDate = useBusinessDate();
  const [confirming, setConfirming] = useState(false);

  return (
    <>
      <PageHeader
        title="End of day"
        description="Every posting carries the business date. End-of-day closes it: the date moves to the next working day, then interest, dormancy, cash reconciliation, the GL snapshot and the ledger check run for the closed date."
        actions={
          canManage ? (
            <Button onClick={() => setConfirming(true)} disabled={!businessDate.data}>
              Run end-of-day
            </Button>
          ) : null
        }
      />
      {businessDate.isError ? <Alert tone="danger" className="mb-4">{errorMessage(businessDate.error)}</Alert> : null}
      {businessDate.data ? (
        <div className="mb-6 grid gap-4 sm:grid-cols-3">
          <StatCard label="Business date" value={formatDate(businessDate.data.businessDate)} hint="Today's postings carry this date" />
          <StatCard label="Next business date" value={formatDate(businessDate.data.nextBusinessDate)} hint="Where end-of-day moves it" />
          <StatCard label="Last closed" value={formatDate(businessDate.data.previousBusinessDate)} />
        </div>
      ) : null}
      <RunsCard />
      <div className="mt-6 grid gap-6 lg:grid-cols-2">
        {businessDate.data ? <WorkingWeekCard businessDate={businessDate.data} /> : null}
        <HolidaysCard />
      </div>
      {confirming && businessDate.data ? <StartDialog businessDate={businessDate.data} onClose={() => setConfirming(false)} /> : null}
    </>
  );
}

function StartDialog({ businessDate, onClose }: { businessDate: BusinessDate; onClose: () => void }) {
  const queryClient = useQueryClient();
  const start = useMutation({
    mutationFn: () => bff<EodRun>('/operations/eod', { method: 'POST' }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['business-date'] });
      void queryClient.invalidateQueries({ queryKey: ['eod-runs'] });
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={start.isPending}
      title={`Close ${formatDate(businessDate.businessDate)}?`}
      description={`The business date moves to ${formatDate(businessDate.nextBusinessDate)} at once; postings from then on carry the new date.`}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={start.isPending}>
            Cancel
          </Button>
          <Button onClick={() => start.mutate()} loading={start.isPending}>
            Close the day
          </Button>
        </>
      }
    >
      <div className="grid gap-3">
        <p className="text-sm text-muted-foreground">Every teller till must be closed first. A step that fails can be resumed; nothing is done twice.</p>
        {start.isError ? <Alert tone="danger">{errorMessage(start.error)}</Alert> : null}
      </div>
    </Modal>
  );
}

function RunsCard() {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<EodRun | null>(null);
  const runs = useQuery({
    queryKey: ['eod-runs', page],
    queryFn: ({ signal }) => bff<Page<EodRun>>(`/operations/eod${query({ page, size: 10 })}`, { signal }),
    placeholderData: keepPreviousData,
    // A run proceeds in the background after the date rolls: follow it until it ends.
    refetchInterval: (current) => (current.state.data?.items.some((run) => run.status === 'RUNNING') ? 2_000 : false),
  });
  const shown = selected ? (runs.data?.items.find((run) => run.id === selected.id) ?? selected) : null;

  const columns: ColumnDef<EodRun, unknown>[] = [
    { header: 'Closed date', cell: ({ row }) => <span className="font-medium">{formatDate(row.original.businessDate)}</span> },
    { header: 'Started', cell: ({ row }) => formatDateTime(row.original.startedAt) },
    { header: 'Finished', cell: ({ row }) => formatDateTime(row.original.finishedAt) },
    { header: 'Attempts', cell: ({ row }) => row.original.attempts },
    {
      header: 'Status',
      cell: ({ row }) => (
        <span className="grid gap-1">
          <StatusBadge status={row.original.status} />
          {row.original.failedStep ? <span className="text-xs text-destructive">{humanize(row.original.failedStep)}</span> : null}
        </span>
      ),
    },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>End-of-day runs</CardTitle>
        <CardDescription>Open a run to see its steps. A failed run is resumed from where it stopped.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        {runs.isError ? <Alert tone="danger">{errorMessage(runs.error)}</Alert> : null}
        <DataTable
          caption="End-of-day runs"
          columns={columns}
          data={runs.data?.items}
          loading={runs.isLoading}
          pageInfo={runs.data}
          onPageChange={setPage}
          onRowClick={setSelected}
          getRowId={(run) => run.id}
          emptyTitle="End-of-day has not run yet"
        />
      </CardContent>
      {shown ? <RunDialog run={shown} onClose={() => setSelected(null)} /> : null}
    </Card>
  );
}

function RunDialog({ run, onClose }: { run: EodRun; onClose: () => void }) {
  const queryClient = useQueryClient();
  const canManage = useCan(Permission.operationsManage);
  const resume = useMutation({
    mutationFn: () => bff<EodRun>(`/operations/eod/${run.id}/resume`, { method: 'POST' }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['eod-runs'] }),
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={resume.isPending}
      className="max-w-2xl"
      title={`End-of-day for ${formatDate(run.businessDate)}`}
      description={<StatusBadge status={run.status} />}
      footer={
        <>
          <Button variant="outline" onClick={onClose}>
            Close
          </Button>
          {canManage && run.status === 'FAILED' ? (
            <Button onClick={() => resume.mutate()} loading={resume.isPending}>
              Resume
            </Button>
          ) : null}
        </>
      }
    >
      <div className="grid gap-3">
        {resume.isError ? <Alert tone="danger">{errorMessage(resume.error)}</Alert> : null}
        {run.failureMessage ? <Alert tone="danger" title={`Stopped at ${humanize(run.failedStep)}`}>{run.failureMessage}</Alert> : null}
        <ol className="grid gap-2">
          {run.steps.map((step) => (
            <li key={step.code} className="rounded-md border p-3">
              <div className="flex items-center justify-between gap-2">
                <span className="font-medium">{humanize(step.code)}</span>
                <StatusBadge status={step.status} />
              </div>
              {step.result ? (
                <p className="mt-1 text-sm text-muted-foreground">
                  {Object.entries(step.result)
                    .map(([key, value]) => `${humanize(key)}: ${String(value)}`)
                    .join(' · ')}
                </p>
              ) : null}
              {step.error ? <p className="mt-1 text-sm text-destructive">{step.error}</p> : null}
            </li>
          ))}
        </ol>
      </div>
    </Modal>
  );
}

function WorkingWeekCard({ businessDate }: { businessDate: BusinessDate }) {
  const queryClient = useQueryClient();
  const canManage = useCan(Permission.operationsManage);
  const [days, setDays] = useState<string[]>(businessDate.workingDays);
  const changed = days.length !== businessDate.workingDays.length || days.some((day) => !businessDate.workingDays.includes(day));
  const save = useMutation({
    mutationFn: () =>
      bff<BusinessDate>('/operations/working-week', { method: 'PUT', body: { workingDays: days, version: businessDate.calendarVersion } }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['business-date'] }),
  });
  const toggle = (day: string, on: boolean) => setDays((current) => (on ? [...current, day] : current.filter((value) => value !== day)));

  return (
    <Card>
      <CardHeader>
        <CardTitle>Working week</CardTitle>
        <CardDescription>End-of-day moves the business date to the next working day that is not a holiday.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        {save.isError ? <Alert tone="danger">{errorMessage(save.error)}</Alert> : null}
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
          {DAYS.map((day) => (
            <Checkbox key={day} label={humanize(day)} checked={days.includes(day)} disabled={!canManage} onChange={(event) => toggle(day, event.target.checked)} />
          ))}
        </div>
        {canManage ? (
          <div>
            <Button size="sm" onClick={() => save.mutate()} disabled={!changed || days.length === 0} loading={save.isPending}>
              Save working week
            </Button>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

function HolidaysCard() {
  const queryClient = useQueryClient();
  const canManage = useCan(Permission.operationsManage);
  const thisYear = new Date().getFullYear();
  const [year, setYear] = useState(thisYear);
  const [date, setDate] = useState('');
  const [name, setName] = useState('');
  const holidays = useQuery({
    queryKey: ['holidays', year],
    queryFn: ({ signal }) => bff<Holiday[]>(`/operations/holidays${query({ year })}`, { signal }),
  });
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['holidays'] });
    void queryClient.invalidateQueries({ queryKey: ['business-date'] });
  };
  const add = useMutation({
    mutationFn: () => bff<Holiday>('/operations/holidays', { body: { date, name: name.trim() } }),
    onSuccess: () => {
      setDate('');
      setName('');
      refresh();
    },
  });
  const remove = useMutation({
    mutationFn: (day: string) => bff<void>(`/operations/holidays/${day}`, { method: 'DELETE' }),
    onSuccess: refresh,
  });

  const columns: ColumnDef<Holiday, unknown>[] = [
    { header: 'Date', cell: ({ row }) => formatDate(row.original.date) },
    { header: 'Holiday', cell: ({ row }) => row.original.name },
    {
      id: 'actions',
      header: '',
      cell: ({ row }) =>
        canManage ? (
          <Button size="sm" variant="ghost" onClick={() => remove.mutate(row.original.date)} loading={remove.isPending && remove.variables === row.original.date}>
            Remove
          </Button>
        ) : null,
    },
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Holidays</CardTitle>
        <CardDescription>Only future dates can be added or removed; a closed day stays as it was.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        <FormField label="Year" className="w-32">
          {(control) => (
            <Select {...control} value={year} onChange={(event) => setYear(Number(event.target.value))}>
              {[thisYear - 1, thisYear, thisYear + 1, thisYear + 2].map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        {holidays.isError ? <Alert tone="danger">{errorMessage(holidays.error)}</Alert> : null}
        {add.isError ? <Alert tone="danger">{errorMessage(add.error)}</Alert> : null}
        {remove.isError ? <Alert tone="danger">{errorMessage(remove.error)}</Alert> : null}
        <DataTable caption="Holidays" columns={columns} data={holidays.data} loading={holidays.isLoading} getRowId={(holiday) => holiday.date} emptyTitle="No holidays this year" />
        {canManage ? (
          <form
            className="grid gap-3 sm:grid-cols-[10rem_1fr_auto] sm:items-end"
            onSubmit={(event) => {
              event.preventDefault();
              add.mutate();
            }}
          >
            <FormField label="Date">
              {(control) => <Input {...control} type="date" value={date} onChange={(event) => setDate(event.target.value)} />}
            </FormField>
            <FormField label="Name">
              {(control) => <Input {...control} maxLength={100} value={name} onChange={(event) => setName(event.target.value)} />}
            </FormField>
            <Button type="submit" disabled={!date || !name.trim()} loading={add.isPending}>
              Add holiday
            </Button>
          </form>
        ) : null}
      </CardContent>
    </Card>
  );
}
