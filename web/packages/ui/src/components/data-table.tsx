'use client';

import {
  flexRender,
  getCoreRowModel,
  useReactTable,
  type ColumnDef,
  type Row,
} from '@tanstack/react-table';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '../lib/cn';
import { Button } from './button';
import { EmptyState, Skeleton } from './display';

export interface PageInfo {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface DataTableProps<T> {
  columns: ColumnDef<T, unknown>[];
  data: T[] | undefined;
  /** Server-side paging: the table never pages or sorts data itself, so it can't hide records the server returned. */
  pageInfo?: PageInfo | undefined;
  onPageChange?: (page: number) => void;
  loading?: boolean;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  onRowClick?: (row: T) => void;
  getRowId?: (row: T) => string;
  caption?: string;
}

export function DataTable<T>({
  columns,
  data,
  pageInfo,
  onPageChange,
  loading = false,
  emptyTitle = 'Nothing to show',
  emptyDescription,
  onRowClick,
  getRowId,
  caption,
}: DataTableProps<T>) {
  const table = useReactTable({
    data: data ?? [],
    columns,
    getCoreRowModel: getCoreRowModel(),
    manualPagination: true,
    getRowId: getRowId ? (row) => getRowId(row) : undefined,
  });

  const rows = table.getRowModel().rows;
  return (
    <div className="overflow-hidden rounded-lg border bg-card">
      <div className="overflow-x-auto">
        <table className="w-full caption-bottom text-sm">
          {caption ? <caption className="sr-only">{caption}</caption> : null}
          <thead className="bg-muted/60">
            {table.getHeaderGroups().map((group) => (
              <tr key={group.id}>
                {group.headers.map((header) => (
                  <th
                    key={header.id}
                    scope="col"
                    className="h-10 px-4 text-left align-middle text-xs font-medium uppercase tracking-wide text-muted-foreground"
                  >
                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody>
            {loading && !data
              ? Array.from({ length: 5 }, (_, index) => (
                  <tr key={`skeleton-${index}`} className="border-t">
                    {columns.map((_, cell) => (
                      <td key={cell} className="px-4 py-3">
                        <Skeleton className="h-4 w-full max-w-40" />
                      </td>
                    ))}
                  </tr>
                ))
              : rows.map((row: Row<T>) => (
                  <tr
                    key={row.id}
                    className={cn('border-t transition-colors', onRowClick && 'cursor-pointer hover:bg-muted/50')}
                    onClick={onRowClick ? () => onRowClick(row.original) : undefined}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <td key={cell.id} className="px-4 py-3 align-middle">
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                ))}
          </tbody>
        </table>
      </div>
      {!loading && data && data.length === 0 ? <EmptyState title={emptyTitle} description={emptyDescription} /> : null}
      {pageInfo && onPageChange ? <Pagination pageInfo={pageInfo} onPageChange={onPageChange} /> : null}
    </div>
  );
}

export function Pagination({ pageInfo, onPageChange }: { pageInfo: PageInfo; onPageChange: (page: number) => void }) {
  const { page, size, totalItems, totalPages } = pageInfo;
  const first = totalItems === 0 ? 0 : page * size + 1;
  const last = Math.min(totalItems, (page + 1) * size);
  return (
    <div className="flex items-center justify-between gap-4 border-t px-4 py-2 text-sm text-muted-foreground">
      <span aria-live="polite">
        {totalItems === 0 ? 'No results' : `${first}–${last} of ${totalItems.toLocaleString()}`}
      </span>
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="sm" disabled={page <= 0} onClick={() => onPageChange(page - 1)} aria-label="Previous page">
          <ChevronLeft aria-hidden="true" />
        </Button>
        <span className="tabular-nums">
          {totalPages === 0 ? 0 : page + 1} / {totalPages}
        </span>
        <Button
          variant="ghost"
          size="sm"
          disabled={page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
          aria-label="Next page"
        >
          <ChevronRight aria-hidden="true" />
        </Button>
      </div>
    </div>
  );
}
