import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ColumnDef } from '@tanstack/react-table';
import { describe, expect, it, vi } from 'vitest';
import { Button } from '../src/components/button';
import { DataTable } from '../src/components/data-table';
import { StatusBadge, statusTone } from '../src/components/display';
import { FormField, Input } from '../src/components/form-controls';
import { Money } from '../src/components/money';
import { Modal } from '../src/components/overlay';

describe('Button', () => {
  it('is disabled and busy while loading, so an action cannot be submitted twice', async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Approve
      </Button>,
    );
    const button = screen.getByRole('button', { name: 'Approve' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });
});

describe('FormField', () => {
  it('wires the label, error and invalid state to the control', () => {
    render(
      <FormField label="Username" error="must not be blank" required>
        {(control) => <Input {...control} />}
      </FormField>,
    );
    const input = screen.getByLabelText(/Username/);
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('must not be blank');
    expect(screen.getByRole('alert')).toHaveTextContent('must not be blank');
  });
});

describe('StatusBadge', () => {
  it('maps statuses to tones and labels', () => {
    render(<StatusBadge status="PENDING_REVIEW" />);
    expect(screen.getByText('Pending review')).toBeInTheDocument();
    expect(statusTone('FROZEN')).toBe('danger');
    expect(statusTone('VERIFIED')).toBe('success');
    expect(statusTone('SOMETHING_NEW')).toBe('neutral');
  });
});

describe('Money', () => {
  it('renders server amounts and highlights negatives', () => {
    render(<Money amount="-25.00" currency="GHS" locale="en-GH" />);
    const amount = screen.getByText('-GH₵25.00');
    expect(amount).toHaveClass('text-destructive');
  });
});

interface Row {
  id: string;
  name: string;
}

const columns: ColumnDef<Row, unknown>[] = [{ accessorKey: 'name', header: 'Name' }];

describe('DataTable', () => {
  it('renders rows and pages through the server', async () => {
    const onPageChange = vi.fn();
    render(
      <DataTable
        columns={columns}
        data={[
          { id: '1', name: 'Ama Mensah' },
          { id: '2', name: 'Kofi Boateng' },
        ]}
        pageInfo={{ page: 0, size: 2, totalItems: 5, totalPages: 3 }}
        onPageChange={onPageChange}
      />,
    );
    expect(screen.getByText('Ama Mensah')).toBeInTheDocument();
    expect(screen.getByText('1–2 of 5')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('shows the empty state', () => {
    render(<DataTable columns={columns} data={[]} emptyTitle="No customers found" />);
    expect(screen.getByText('No customers found')).toBeInTheDocument();
  });
});

describe('Modal', () => {
  it('closes on Escape unless an action is in flight', async () => {
    const onClose = vi.fn();
    const { rerender } = render(
      <Modal open onClose={onClose} title="Approve KYC" busy>
        <p>Body</p>
      </Modal>,
    );
    expect(screen.getByRole('dialog', { name: 'Approve KYC' })).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    expect(onClose).not.toHaveBeenCalled();

    rerender(
      <Modal open onClose={onClose} title="Approve KYC">
        <p>Body</p>
      </Modal>,
    );
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
