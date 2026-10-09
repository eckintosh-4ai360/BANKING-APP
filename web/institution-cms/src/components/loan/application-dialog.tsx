'use client';

import { bff, newIdempotencyKey, Permission, type LoanApplicationDetail, type LoanCollateral, type LoanDetail, type LoanGuarantor, type SchedulePreview } from '@banking/api';
import { errorMessage } from '@banking/console';
import { Alert, Badge, Button, DataTable, DetailList, FormField, Input, Modal, Money, Select, StatusBadge, Textarea, formatDate, formatDateTime, humanize } from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { useCan } from '@/lib/me';
import { AMOUNT } from '@/lib/schemas';
import { PreviewSummary } from './schedule-tables';

const UNDECIDED = ['DRAFT', 'SUBMITTED', 'ASSESSED', 'RECOMMENDED'];
const CATEGORIES = ['LAND', 'BUILDING', 'VEHICLE', 'EQUIPMENT', 'INVENTORY', 'SAVINGS', 'HOUSEHOLD', 'OTHER'];

/**
 * An application, its workflow and its security. The buttons follow the user's permissions; the server decides
 * (separation of duties included) and its refusal is shown as is.
 */
export function ApplicationDialog({ applicationId, onClose }: { applicationId: string; onClose: () => void }) {
  const queryClient = useQueryClient();
  const canCreate = useCan(Permission.loanCreate);
  const canAssess = useCan(Permission.loanAssess);
  const canRecommend = useCan(Permission.loanRecommend);
  const canApprove = useCan(Permission.loanApprove);
  const canDisburse = useCan(Permission.loanDisburse);
  const [note, setNote] = useState('');
  const [riskRating, setRiskRating] = useState('MEDIUM');
  const [approvedAmount, setApprovedAmount] = useState('');
  const [approvedInstallments, setApprovedInstallments] = useState('');
  const [firstDueDate, setFirstDueDate] = useState('');
  const [disburseKey] = useState(newIdempotencyKey);
  const path = `/loan-applications/${applicationId}`;
  const detail = useQuery({ queryKey: ['loan-applications', applicationId], queryFn: ({ signal }) => bff<LoanApplicationDetail>(path, { signal }) });
  const preview = useQuery({
    queryKey: ['loan-applications', applicationId, 'preview'],
    queryFn: ({ signal }) => bff<SchedulePreview>(`${path}/schedule-preview`, { signal }),
    enabled: false,
    retry: false,
  });
  const application = detail.data?.application;
  const refresh = (data: LoanApplicationDetail) => {
    queryClient.setQueryData(['loan-applications', applicationId], data);
    void queryClient.invalidateQueries({ queryKey: ['loan-applications'] });
    setNote('');
  };
  const step = useMutation({
    mutationFn: ({ action, body }: { action: string; body: Record<string, unknown> }) =>
      bff<LoanApplicationDetail>(`${path}/${action}`, { body: { ...body, version: application?.version } }),
    onSuccess: refresh,
  });
  const disburse = useMutation({
    mutationFn: () =>
      bff<LoanDetail>(`${path}/disburse`, { body: { ...(firstDueDate ? { firstDueDate } : {}) }, idempotencyKey: disburseKey }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['loan-applications'] });
      void queryClient.invalidateQueries({ queryKey: ['loans'] });
    },
  });
  const verify = useMutation({
    mutationFn: (target: string) => bff<LoanApplicationDetail>(`${path}/${target}/verify`, { method: 'POST' }),
    onSuccess: refresh,
  });
  const release = useMutation({
    mutationFn: (collateralId: string) => bff<LoanApplicationDetail>(`${path}/collateral/${collateralId}/release`, { method: 'POST' }),
    onSuccess: refresh,
  });
  const failure = [step, disburse, verify, release].find((mutation) => mutation.isError)?.error;
  const status = application?.status;
  const awaitingSecond = status === 'RECOMMENDED' && application?.approvedAmount != null;
  const approvalValid = AMOUNT.test(approvedAmount) && /[1-9]/.test(approvedAmount) && Number.isInteger(Number(approvedInstallments)) && Number(approvedInstallments) >= 1;

  return (
    <Modal
      open
      onClose={onClose}
      busy={step.isPending || disburse.isPending}
      className="max-w-4xl"
      title={application ? `${application.applicationNumber} · ${application.customerName ?? ''}` : 'Loan application'}
      description={application ? <StatusBadge status={application.status} /> : null}
      footer={<Button variant="outline" onClick={onClose}>Close</Button>}
    >
      <div className="grid gap-4">
        {detail.isError ? <Alert tone="danger">{errorMessage(detail.error)}</Alert> : null}
        {failure ? <Alert tone="danger">{errorMessage(failure)}</Alert> : null}
        {disburse.isSuccess ? <Alert tone="success">Loan {disburse.data.loan.loanNumber} disbursed.</Alert> : null}
        {application ? (
          <DetailList
            columns={3}
            items={[
              { label: 'Product', value: application.productName ?? application.productCode },
              { label: 'Requested', value: <>{<Money amount={application.requestedAmount} currency={application.currency} />} over {application.requestedInstallments}</> },
              {
                label: 'Approved',
                value: application.approvedAmount ? <>{<Money amount={application.approvedAmount} currency={application.currency} />} over {application.approvedInstallments}</> : '—',
              },
              { label: 'Loan officer', value: application.loanOfficerName ?? '—' },
              { label: 'Risk rating', value: application.riskRating ? humanize(application.riskRating) : '—' },
              { label: 'Second approval', value: application.secondApprovalRequired ? 'Needed' : 'Not needed' },
              { label: 'Purpose', value: application.purpose },
              { label: 'Assessment', value: application.assessmentNote ?? '—' },
              { label: 'First due date', value: application.firstDueDate ? formatDate(application.firstDueDate) : 'One period after disbursement' },
            ]}
          />
        ) : null}
        {detail.data ? <Security detail={detail.data} /> : null}
        {detail.data ? (
          <SecurityTables
            detail={detail.data}
            canVerify={canAssess && UNDECIDED.includes(status ?? '')}
            canRelease={canApprove && (status === 'REJECTED' || status === 'WITHDRAWN')}
            onVerify={(target) => verify.mutate(target)}
            onRelease={(collateralId) => release.mutate(collateralId)}
          />
        ) : null}
        {canCreate && UNDECIDED.includes(status ?? '') && application ? (
          <AddSecurity path={path} currency={application.currency} onAdded={refresh} />
        ) : null}

        {detail.data?.steps.length ? (
          <ol className="grid gap-1 text-sm">
            {detail.data.steps.map((entry) => (
              <li key={`${entry.type}-${entry.occurredAt}`}>
                <Badge tone="neutral">{humanize(entry.type)}</Badge> {entry.actorName ?? entry.actorId} · {formatDateTime(entry.occurredAt)}
                {entry.note ? ` · ${entry.note}` : ''}
              </li>
            ))}
          </ol>
        ) : null}

        {status && !['REJECTED', 'WITHDRAWN', 'DISBURSED'].includes(status) ? (
          <div className="grid gap-3 rounded-md border p-3">
            <FormField label="Note" hint="Recorded with the step; a rejection needs one">
              {(control) => <Textarea {...control} maxLength={1000} value={note} onChange={(event) => setNote(event.target.value)} />}
            </FormField>
            {canAssess && (status === 'SUBMITTED' || status === 'ASSESSED') ? (
              <FormField label="Risk rating" className="w-48">
                {(control) => (
                  <Select {...control} value={riskRating} onChange={(event) => setRiskRating(event.target.value)}>
                    <option value="LOW">Low</option>
                    <option value="MEDIUM">Medium</option>
                    <option value="HIGH">High</option>
                  </Select>
                )}
              </FormField>
            ) : null}
            {canApprove && status === 'RECOMMENDED' && !awaitingSecond ? (
              <div className="grid gap-3 sm:grid-cols-3">
                <FormField label="Approved amount" required>
                  {(control) => <Input {...control} inputMode="decimal" value={approvedAmount} onChange={(event) => setApprovedAmount(event.target.value.trim())} />}
                </FormField>
                <FormField label="Installments" required>
                  {(control) => <Input {...control} inputMode="numeric" value={approvedInstallments} onChange={(event) => setApprovedInstallments(event.target.value.trim())} />}
                </FormField>
                <FormField label="First due date">
                  {(control) => <Input {...control} type="date" value={firstDueDate} onChange={(event) => setFirstDueDate(event.target.value)} />}
                </FormField>
              </div>
            ) : null}
            {canDisburse && status === 'APPROVED' ? (
              <FormField label="First due date" hint="Leave empty to keep the approved date (or one period from today)" className="w-64">
                {(control) => <Input {...control} type="date" value={firstDueDate} onChange={(event) => setFirstDueDate(event.target.value)} />}
              </FormField>
            ) : null}
            <div className="flex flex-wrap gap-2">
              {canCreate && status === 'DRAFT' ? (
                <Button size="sm" onClick={() => step.mutate({ action: 'submit', body: { note: note.trim() || undefined } })}>Submit</Button>
              ) : null}
              {canAssess && (status === 'SUBMITTED' || status === 'ASSESSED') ? (
                <Button size="sm" disabled={!note.trim()} onClick={() => step.mutate({ action: 'assess', body: { riskRating, note: note.trim() } })}>
                  Record assessment
                </Button>
              ) : null}
              {canRecommend && status === 'ASSESSED' ? (
                <Button size="sm" onClick={() => step.mutate({ action: 'recommend', body: { note: note.trim() || undefined } })}>Recommend</Button>
              ) : null}
              {canApprove && status === 'RECOMMENDED' && !awaitingSecond ? (
                <Button
                  size="sm"
                  disabled={!approvalValid}
                  onClick={() =>
                    step.mutate({
                      action: 'approve',
                      body: { approvedAmount, approvedInstallments: Number(approvedInstallments), note: note.trim() || undefined, ...(firstDueDate ? { firstDueDate } : {}) },
                    })
                  }
                >
                  Approve
                </Button>
              ) : null}
              {canApprove && awaitingSecond ? (
                <Button size="sm" onClick={() => step.mutate({ action: 'second-approval', body: { note: note.trim() || undefined } })}>Give second approval</Button>
              ) : null}
              {canDisburse && status === 'APPROVED' ? (
                <Button size="sm" onClick={() => disburse.mutate()} loading={disburse.isPending} disabled={disburse.isSuccess}>
                  Disburse
                </Button>
              ) : null}
              <Button size="sm" variant="outline" onClick={() => void preview.refetch()} loading={preview.isFetching}>
                Schedule preview
              </Button>
              {canApprove && status !== 'DRAFT' ? (
                <Button size="sm" variant="destructive" disabled={!note.trim()} onClick={() => step.mutate({ action: 'reject', body: { note: note.trim() } })}>
                  Reject
                </Button>
              ) : null}
              {canCreate ? (
                <Button size="sm" variant="outline" onClick={() => step.mutate({ action: 'withdraw', body: { note: note.trim() || undefined } })}>
                  Withdraw
                </Button>
              ) : null}
            </div>
          </div>
        ) : null}
        {preview.isError ? <Alert tone="danger">{errorMessage(preview.error)}</Alert> : null}
        {preview.data && application ? <PreviewSummary preview={preview.data} currency={application.currency} /> : null}
      </div>
    </Modal>
  );
}

function Security({ detail }: { detail: LoanApplicationDetail }) {
  const { security, application } = detail;
  return (
    <p className="text-sm">
      Guarantors verified: {security.guarantorsVerified} of {security.guarantorsRequired} needed · Collateral verified:{' '}
      <Money amount={security.collateralVerified} currency={application.currency} /> of{' '}
      <Money amount={security.collateralNeeded} currency={application.currency} /> needed
    </p>
  );
}

function SecurityTables({
  detail,
  canVerify,
  canRelease,
  onVerify,
  onRelease,
}: {
  detail: LoanApplicationDetail;
  canVerify: boolean;
  canRelease: boolean;
  onVerify: (target: string) => void;
  onRelease: (collateralId: string) => void;
}) {
  const currency = detail.application.currency;
  const guarantorColumns: ColumnDef<LoanGuarantor, unknown>[] = [
    { header: 'Guarantor', cell: ({ row }) => row.original.fullName },
    { header: 'Relationship', cell: ({ row }) => row.original.relationship },
    { header: 'Guarantees', cell: ({ row }) => <Money amount={row.original.guaranteedAmount} currency={currency} /> },
    {
      header: 'Verified',
      cell: ({ row }) =>
        row.original.verifiedAt ? formatDate(row.original.verifiedAt) : canVerify ? (
          <Button size="sm" variant="outline" onClick={() => onVerify(`guarantors/${row.original.id}`)}>Verify</Button>
        ) : 'No',
    },
  ];
  const collateralColumns: ColumnDef<LoanCollateral, unknown>[] = [
    { header: 'Collateral', cell: ({ row }) => `${humanize(row.original.category)} · ${row.original.description}` },
    { header: 'Forced-sale value', cell: ({ row }) => <Money amount={row.original.forcedSaleValue} currency={currency} /> },
    { header: 'Status', cell: ({ row }) => <StatusBadge status={row.original.status} /> },
    {
      header: 'Verified',
      cell: ({ row }) =>
        row.original.verifiedAt ? formatDate(row.original.verifiedAt) : canVerify ? (
          <Button size="sm" variant="outline" onClick={() => onVerify(`collateral/${row.original.id}`)}>Verify</Button>
        ) : 'No',
    },
    {
      header: '',
      id: 'release',
      cell: ({ row }) =>
        canRelease && row.original.status === 'PLEDGED' ? (
          <Button size="sm" variant="outline" onClick={() => onRelease(row.original.id)}>Release</Button>
        ) : null,
    },
  ];
  return (
    <div className="grid gap-3 lg:grid-cols-2">
      <DataTable caption="Guarantors" columns={guarantorColumns} data={detail.guarantors} getRowId={(row) => row.id} emptyTitle="No guarantors" />
      <DataTable caption="Collateral" columns={collateralColumns} data={detail.collateral} getRowId={(row) => row.id} emptyTitle="No collateral" />
    </div>
  );
}

function AddSecurity({ path, currency, onAdded }: { path: string; currency: string; onAdded: (detail: LoanApplicationDetail) => void }) {
  const [kind, setKind] = useState<'guarantor' | 'collateral'>('guarantor');
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [relationship, setRelationship] = useState('');
  const [category, setCategory] = useState('INVENTORY');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [forcedSaleValue, setForcedSaleValue] = useState('');
  const [valuationDate, setValuationDate] = useState('');
  const add = useMutation({
    mutationFn: () =>
      kind === 'guarantor'
        ? bff<LoanApplicationDetail>(`${path}/guarantors`, {
            body: { fullName: fullName.trim(), phone: phone.trim() || undefined, relationship: relationship.trim(), guaranteedAmount: amount },
          })
        : bff<LoanApplicationDetail>(`${path}/collateral`, {
            body: { category, description: description.trim(), estimatedValue: amount, forcedSaleValue, valuationDate },
          }),
    onSuccess: (detail) => {
      onAdded(detail);
      setFullName('');
      setPhone('');
      setRelationship('');
      setDescription('');
      setAmount('');
      setForcedSaleValue('');
    },
  });
  const valid =
    AMOUNT.test(amount) &&
    (kind === 'guarantor' ? !!fullName.trim() && !!relationship.trim() : !!description.trim() && AMOUNT.test(forcedSaleValue) && !!valuationDate);

  return (
    <div className="grid gap-3 rounded-md border p-3">
      {add.isError ? <Alert tone="danger">{errorMessage(add.error)}</Alert> : null}
      <div className="grid gap-3 sm:grid-cols-4">
        <FormField label="Add">
          {(control) => (
            <Select {...control} value={kind} onChange={(event) => setKind(event.target.value as 'guarantor' | 'collateral')}>
              <option value="guarantor">Guarantor</option>
              <option value="collateral">Collateral</option>
            </Select>
          )}
        </FormField>
        {kind === 'guarantor' ? (
          <>
            <FormField label="Full name" required>
              {(control) => <Input {...control} value={fullName} onChange={(event) => setFullName(event.target.value)} />}
            </FormField>
            <FormField label="Phone">
              {(control) => <Input {...control} value={phone} onChange={(event) => setPhone(event.target.value)} />}
            </FormField>
            <FormField label="Relationship" required>
              {(control) => <Input {...control} value={relationship} onChange={(event) => setRelationship(event.target.value)} />}
            </FormField>
            <FormField label={`Guarantees (${currency})`} required>
              {(control) => <Input {...control} inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value.trim())} />}
            </FormField>
          </>
        ) : (
          <>
            <FormField label="Category">
              {(control) => (
                <Select {...control} value={category} onChange={(event) => setCategory(event.target.value)}>
                  {CATEGORIES.map((code) => (
                    <option key={code} value={code}>
                      {humanize(code)}
                    </option>
                  ))}
                </Select>
              )}
            </FormField>
            <FormField label="Description" required>
              {(control) => <Input {...control} value={description} onChange={(event) => setDescription(event.target.value)} />}
            </FormField>
            <FormField label={`Estimated value (${currency})`} required>
              {(control) => <Input {...control} inputMode="decimal" value={amount} onChange={(event) => setAmount(event.target.value.trim())} />}
            </FormField>
            <FormField label="Forced-sale value" required>
              {(control) => <Input {...control} inputMode="decimal" value={forcedSaleValue} onChange={(event) => setForcedSaleValue(event.target.value.trim())} />}
            </FormField>
            <FormField label="Valued on" required>
              {(control) => <Input {...control} type="date" value={valuationDate} onChange={(event) => setValuationDate(event.target.value)} />}
            </FormField>
          </>
        )}
      </div>
      <div>
        <Button size="sm" variant="outline" disabled={!valid} loading={add.isPending} onClick={() => add.mutate()}>
          Add {kind}
        </Button>
      </div>
    </div>
  );
}
