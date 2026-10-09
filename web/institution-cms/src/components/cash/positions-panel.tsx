'use client';

import { bff, query, type CashPosition } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, DataTable, FormField, Input, Money, StatusBadge, formatDate } from '@banking/ui';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { useDrawers, useVaults } from '@/components/cash/cash-points-panel';
import { useBranches } from '@/lib/queries';

/**
 * The end-of-day cash reconciliation: each vault's and drawer's ledger balance next to the cash last counted in it.
 * A break means cash was posted to a drawer after its till was counted, and needs investigating.
 */
export function PositionsPanel() {
  const { nameOf } = useBranches();
  const vaults = useVaults();
  const drawers = useDrawers();
  const [date, setDate] = useState('');
  const positions = useQuery({
    queryKey: ['cash', 'positions', date],
    queryFn: ({ signal }) => bff<CashPosition[]>(`/cash/positions${query({ date })}`, { signal }),
  });
  const breaks = positions.data?.filter((position) => position.status === 'BREAK').length ?? 0;
  const labelOf = (position: CashPosition) =>
    position.cashPointType === 'VAULT'
      ? (vaults.data?.find((vault) => vault.id === position.cashPointId)?.name ?? 'Vault')
      : (drawers.data?.find((drawer) => drawer.id === position.cashPointId)?.code ?? 'Drawer');
  const amount = (value: string | null, currency: string) => (value === null ? '—' : <Money amount={value} currency={currency} />);

  const columns: ColumnDef<CashPosition, unknown>[] = [
    {
      header: 'Cash point',
      cell: ({ row }) => (
        <span className="grid">
          <span className="font-medium">{labelOf(row.original)}</span>
          <span className="text-xs text-muted-foreground">{row.original.cashPointType === 'VAULT' ? 'Vault' : 'Drawer'}</span>
        </span>
      ),
    },
    { header: 'Branch', cell: ({ row }) => nameOf(row.original.branchId) },
    { header: 'Ledger', cell: ({ row }) => amount(row.original.ledgerBalance, row.original.currency) },
    { header: 'Last count', cell: ({ row }) => amount(row.original.countedBalance, row.original.currency) },
    { header: 'Difference', cell: ({ row }) => amount(row.original.difference, row.original.currency) },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  ];

  return (
    <div className="grid gap-4">
      <FormField label="Business date" hint="Leave empty for the last closed date" className="w-56">
        {(control) => <Input {...control} type="date" value={date} onChange={(event) => setDate(event.target.value)} />}
      </FormField>
      {positions.data && positions.data.length > 0 ? (
        <p className="text-sm text-muted-foreground">Cash positions at the close of {formatDate(positions.data[0]?.businessDate)}.</p>
      ) : null}
      {breaks > 0 ? (
        <Alert tone="danger" title={`${breaks} cash ${breaks === 1 ? 'break' : 'breaks'}`}>
          A drawer's ledger balance differs from its last count: cash was posted to it after the till was closed. The break is in the audit log.
        </Alert>
      ) : null}
      {positions.isError ? <Alert tone="danger">{errorMessage(positions.error)}</Alert> : null}
      <DataTable
        caption="Cash positions"
        columns={columns}
        data={positions.data}
        loading={positions.isLoading}
        getRowId={(position) => position.cashPointId}
        emptyTitle="No end-of-day has reconciled cash yet"
      />
    </div>
  );
}
