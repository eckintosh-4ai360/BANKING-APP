'use client';

import { bff, Permission, type ApprovalPolicy } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, Card, CardContent, CardHeader, CardTitle, Checkbox, FormField, Input, Modal, Money, humanize } from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useCan, useMe } from '@/lib/me';
import { AMOUNT } from '@/lib/schemas';

/** Thresholds above which withdrawals and transfers wait for a checker. Administrators edit them. */
export function ThresholdsCard() {
  const me = useMe();
  const canManage = useCan(Permission.settingsManage);
  const policies = useQuery({ queryKey: ['approval-policies'], queryFn: ({ signal }) => bff<ApprovalPolicy[]>('/approval-policies', { signal }) });
  const [editing, setEditing] = useState<{ requestType: 'CASH_WITHDRAWAL' | 'TRANSFER'; policy?: ApprovalPolicy } | null>(null);
  const currencyOf = (policy: ApprovalPolicy | undefined) => policy?.currency ?? 'GHS';

  return (
    <Card className="mt-6">
      <CardHeader>
        <CardTitle>Approval thresholds</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-3">
        {policies.isError ? <Alert tone="danger">{errorMessage(policies.error)}</Alert> : null}
        {(['CASH_WITHDRAWAL', 'TRANSFER'] as const).map((requestType) => {
          const policy = policies.data?.find((candidate) => candidate.requestType === requestType);
          return (
            <div key={requestType} className="flex flex-wrap items-center justify-between gap-2 text-sm">
              <span className="font-medium">{humanize(requestType)}</span>
              <span className="text-muted-foreground">
                {policy && policy.active ? (
                  <>
                    From <Money amount={policy.thresholdAmount} currency={policy.currency} /> a checker must approve
                  </>
                ) : (
                  'No threshold: posted immediately'
                )}
              </span>
              {canManage ? (
                <Button size="sm" variant="ghost" onClick={() => setEditing({ requestType, policy })}>
                  Edit
                </Button>
              ) : null}
            </div>
          );
        })}
      </CardContent>
      {editing ? (
        <ThresholdDialog
          requestType={editing.requestType}
          policy={editing.policy}
          currency={currencyOf(editing.policy)}
          institution={me.institutionName}
          onClose={() => setEditing(null)}
        />
      ) : null}
    </Card>
  );
}

function ThresholdDialog({
  requestType,
  policy,
  currency,
  institution,
  onClose,
}: {
  requestType: 'CASH_WITHDRAWAL' | 'TRANSFER';
  policy: ApprovalPolicy | undefined;
  currency: string;
  institution: string;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState(policy?.thresholdAmount ?? '');
  const [code, setCode] = useState(currency);
  const [active, setActive] = useState(policy?.active ?? true);
  const valid = AMOUNT.test(amount.trim()) && /[1-9]/.test(amount) && /^[A-Z]{3}$/.test(code);
  const save = useMutation({
    mutationFn: () => bff<ApprovalPolicy>(`/approval-policies/${requestType}/${code}`, { method: 'PUT', body: { thresholdAmount: amount.trim(), active } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['approval-policies'] });
      onClose();
    },
  });
  return (
    <Modal
      open
      onClose={onClose}
      busy={save.isPending}
      title={`${humanize(requestType)} threshold`}
      description={`At or above this amount, a second person at ${institution} must approve before money moves.`}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={save.isPending}>
            Cancel
          </Button>
          <Button loading={save.isPending} disabled={!valid} onClick={() => save.mutate()}>
            Save
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {save.isError ? <Alert tone="danger">{errorMessage(save.error)}</Alert> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <FormField label="Threshold" required>
            {(control) => <Input {...control} inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value)} />}
          </FormField>
          <FormField label="Currency" required>
            {(control) => <Input {...control} value={code} maxLength={3} disabled={Boolean(policy)} onChange={(event) => setCode(event.target.value.toUpperCase())} />}
          </FormField>
        </div>
        <Checkbox label="Active" checked={active} onChange={(event) => setActive(event.target.checked)} />
      </div>
    </Modal>
  );
}
