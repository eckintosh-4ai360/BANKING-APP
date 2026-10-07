'use client';

import { Permission, type CustomerDetail } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, Card, CardContent, CardHeader, CardTitle, Modal } from '@banking/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { useCan } from '@/lib/me';

export function customerKey(id: string) {
  return ['customer', id] as const;
}

/**
 * Whether the user may capture records for this customer. Mirrors the backend rule: customer.edit always, or
 * customer.create while the customer is still being onboarded (PENDING).
 */
export function useCanCapture(customer: Pick<CustomerDetail, 'status'>): boolean {
  const canEdit = useCan(Permission.customerEdit);
  const canCreate = useCan(Permission.customerCreate);
  return canEdit || (canCreate && customer.status === 'PENDING');
}

/** Refreshes the customer record (and lists showing it) after a change. */
export function useCustomerRefresh(customerId: string) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: customerKey(customerId) });
    void queryClient.invalidateQueries({ queryKey: ['customers'] });
    void queryClient.invalidateQueries({ queryKey: ['customer-kyc-cases', customerId] });
  };
}

export function Section({ title, action, children }: { title: string; action?: ReactNode; children: ReactNode }) {
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between gap-4">
        <CardTitle>{title}</CardTitle>
        {action}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

/** A destructive or significant action behind an explicit confirmation. */
export function ConfirmAction({
  label,
  title,
  description,
  confirmLabel = 'Confirm',
  onConfirm,
  onDone,
  variant = 'outline',
  destructive = false,
}: {
  label: ReactNode;
  title: string;
  description: ReactNode;
  confirmLabel?: string;
  onConfirm: () => Promise<unknown>;
  onDone?: () => void;
  variant?: 'outline' | 'ghost' | 'destructive' | 'primary';
  destructive?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const action = useMutation({
    mutationFn: onConfirm,
    onSuccess: () => {
      setOpen(false);
      onDone?.();
    },
  });
  return (
    <>
      <Button variant={variant} size="sm" onClick={() => setOpen(true)}>
        {label}
      </Button>
      <Modal
        open={open}
        onClose={() => {
          setOpen(false);
          action.reset();
        }}
        busy={action.isPending}
        title={title}
        description={description}
        footer={
          <>
            <Button variant="outline" onClick={() => setOpen(false)} disabled={action.isPending}>
              Cancel
            </Button>
            <Button variant={destructive ? 'destructive' : 'primary'} loading={action.isPending} onClick={() => action.mutate()}>
              {confirmLabel}
            </Button>
          </>
        }
      >
        {action.isError ? <Alert tone="danger">{errorMessage(action.error)}</Alert> : null}
      </Modal>
    </>
  );
}
