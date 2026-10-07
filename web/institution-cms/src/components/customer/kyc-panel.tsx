'use client';

import { bff, Permission, type CustomerDetail, type KycCase, type KycCaseSummary } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Button, DataTable, FormField, Modal, Select, StatusBadge, formatDateTime, humanize } from '@banking/ui';
import { useMutation, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useCan } from '@/lib/me';
import { useKycTiers } from '@/lib/queries';
import { Section, useCanCapture, useCustomerRefresh } from './shared';

const columns: ColumnDef<KycCaseSummary, unknown>[] = [
  { header: 'Case', cell: ({ row }) => humanize(row.original.caseType) },
  { header: 'Target tier', cell: ({ row }) => row.original.targetTierCode },
  { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
  { header: 'Submitted', cell: ({ row }) => formatDateTime(row.original.submittedAt) },
  { header: 'Opened', cell: ({ row }) => formatDateTime(row.original.createdAt) },
];

export function KycPanel({ customer }: { customer: CustomerDetail }) {
  const router = useRouter();
  const canView = useCan(Permission.kycView);
  const canCapture = useCanCapture(customer);
  const [opening, setOpening] = useState(false);
  const cases = useQuery({
    queryKey: ['customer-kyc-cases', customer.id],
    queryFn: ({ signal }) => bff<KycCaseSummary[]>(`/customers/${customer.id}/kyc-cases`, { signal }),
    enabled: canView,
  });
  const hasOpenCase = (cases.data ?? []).some((kycCase) => ['OPEN', 'PENDING_REVIEW', 'RETURNED'].includes(kycCase.status));

  return (
    <Section
      title="KYC cases"
      action={
        canCapture && !hasOpenCase && customer.status !== 'CLOSED' ? (
          <Button size="sm" onClick={() => setOpening(true)}>
            Open KYC case
          </Button>
        ) : null
      }
    >
      {!canView ? (
        <p className="text-sm text-muted-foreground">You don't have access to KYC cases.</p>
      ) : (
        <>
          {cases.isError ? <Alert tone="danger">{errorMessage(cases.error)}</Alert> : null}
          <DataTable
            caption="KYC cases"
            columns={columns}
            data={cases.data}
            loading={cases.isLoading}
            onRowClick={(kycCase) => router.push(`/kyc/${kycCase.id}`)}
            getRowId={(kycCase) => kycCase.id}
            emptyTitle="No KYC cases yet"
            emptyDescription="Open an onboarding case once the identification, address and documents are captured."
          />
        </>
      )}
      <OpenCaseDialog open={opening} customer={customer} onClose={() => setOpening(false)} onOpened={(kycCase) => router.push(`/kyc/${kycCase.id}`)} />
    </Section>
  );
}

function OpenCaseDialog({ open, customer, onClose, onOpened }: { open: boolean; customer: CustomerDetail; onClose: () => void; onOpened: (kycCase: KycCase) => void }) {
  const tiers = useKycTiers();
  const refresh = useCustomerRefresh(customer.id);
  const defaultType = customer.kycStatus === 'VERIFIED' ? 'UPGRADE' : 'ONBOARDING';
  const [caseType, setCaseType] = useState<string>(defaultType);
  const [targetTierCode, setTargetTierCode] = useState('');
  const activeTiers = (tiers.data ?? []).filter((tier) => tier.active).sort((a, b) => a.tierRank - b.tierRank);
  const chosenTier = targetTierCode || activeTiers[0]?.code || '';

  const openCase = useMutation({
    mutationFn: () => bff<KycCase>(`/customers/${customer.id}/kyc-cases`, { method: 'POST', body: { caseType, targetTierCode: chosenTier } }),
    onSuccess: (kycCase) => {
      refresh();
      onClose();
      onOpened(kycCase);
    },
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      busy={openCase.isPending}
      title="Open KYC case"
      description="The case collects the checks and documents needed for the target tier, then goes to a reviewer."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={openCase.isPending}>
            Cancel
          </Button>
          <Button loading={openCase.isPending} disabled={!chosenTier} onClick={() => openCase.mutate()}>
            Open case
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {openCase.isError ? <Alert tone="danger">{errorMessage(openCase.error)}</Alert> : null}
        <FormField label="Case type" required>
          {(control) => (
            <Select {...control} value={caseType} onChange={(event) => setCaseType(event.target.value)}>
              {customer.kycStatus !== 'VERIFIED' ? <option value="ONBOARDING">Onboarding</option> : null}
              {customer.kycStatus === 'VERIFIED' ? (
                <>
                  <option value="UPGRADE">Upgrade tier</option>
                  <option value="UPDATE">Update identity details</option>
                  <option value="PERIODIC_REVIEW">Periodic review</option>
                </>
              ) : null}
            </Select>
          )}
        </FormField>
        <FormField label="Target tier" required hint="Tier requirements are configured under Settings.">
          {(control) => (
            <Select {...control} value={chosenTier} onChange={(event) => setTargetTierCode(event.target.value)}>
              {activeTiers.map((tier) => (
                <option key={tier.code} value={tier.code}>
                  {tier.name} ({tier.code})
                </option>
              ))}
            </Select>
          )}
        </FormField>
      </div>
    </Modal>
  );
}
