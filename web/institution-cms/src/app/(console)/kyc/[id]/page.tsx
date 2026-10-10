'use client';

import { bff, Permission, type KycCase } from '@banking/api';
import { errorMessage } from '@banking/console';
import {
  Alert,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  DetailList,
  EmptyState,
  FormField,
  Modal,
  PageHeader,
  Select,
  Skeleton,
  StatusBadge,
  Textarea,
  formatDateTime,
  humanize,
} from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CircleCheck, CircleDashed } from 'lucide-react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { SignUpAnswers } from '@/components/channel/sign-up-answers';
import { useCan, useMe } from '@/lib/me';
import { useBranches } from '@/lib/queries';

type DecisionKind = 'return' | 'approve' | 'reject' | 'cancel' | 'check';

export default function KycCasePage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const me = useMe();
  const { nameOf } = useBranches();
  const canCapture = useCan(Permission.customerCreate, Permission.customerEdit);
  const canReview = useCan(Permission.kycReview);
  const canApprove = useCan(Permission.kycApprove);
  const [dialog, setDialog] = useState<DecisionKind | null>(null);

  const kycCase = useQuery({ queryKey: ['kyc-case', id], queryFn: ({ signal }) => bff<KycCase>(`/kyc/cases/${id}`, { signal }) });

  function updated(next: KycCase) {
    queryClient.setQueryData(['kyc-case', id], next);
    void queryClient.invalidateQueries({ queryKey: ['kyc-cases'] });
    void queryClient.invalidateQueries({ queryKey: ['customer', next.customerId] });
    void queryClient.invalidateQueries({ queryKey: ['customer-kyc-cases', next.customerId] });
  }

  const identityCheck = useMutation({
    mutationFn: () => bff<KycCase>(`/kyc/cases/${id}/identity-check`, { method: 'POST' }),
    onSuccess: updated,
  });
  const submit = useMutation({
    mutationFn: () => bff<KycCase>(`/kyc/cases/${id}/submit`, { method: 'POST' }),
    onSuccess: updated,
  });

  if (kycCase.isPending) {
    return <Skeleton className="h-64" />;
  }
  if (kycCase.isError) {
    return <Alert tone="danger">{errorMessage(kycCase.error)}</Alert>;
  }

  const data = kycCase.data;
  const editable = data.status === 'OPEN' || data.status === 'RETURNED';
  const inReview = data.status === 'PENDING_REVIEW';
  const submittedByMe = data.submittedBy === me.id;
  const readyToSubmit = data.requirements.every((requirement) => requirement.metForSubmission);
  const readyToApprove = data.requirements.every((requirement) => requirement.metForApproval);
  const actionError = identityCheck.error ?? submit.error;

  return (
    <>
      <PageHeader
        title={`${humanize(data.caseType)} case`}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <Link href={`/customers/${data.customerId}`} className="font-medium text-primary hover:underline">
              {data.customerName}
            </Link>
            <span className="font-mono">{data.customerNumber}</span>
            <StatusBadge status={data.status} />
          </span>
        }
        actions={
          <>
            {editable && (canCapture || canReview) ? (
              <Button variant="outline" loading={identityCheck.isPending} onClick={() => identityCheck.mutate()}>
                Run identity check
              </Button>
            ) : null}
            {editable && canCapture ? (
              <>
                <Button variant="ghost" onClick={() => setDialog('cancel')}>
                  Cancel case
                </Button>
                <Button loading={submit.isPending} disabled={!readyToSubmit} onClick={() => submit.mutate()} title={readyToSubmit ? undefined : 'Complete the requirements first'}>
                  Submit for review
                </Button>
              </>
            ) : null}
            {inReview && canReview ? (
              <>
                <Button variant="outline" onClick={() => setDialog('check')}>
                  Record manual check
                </Button>
                <Button variant="outline" onClick={() => setDialog('return')}>
                  Return for correction
                </Button>
              </>
            ) : null}
            {inReview && canApprove ? (
              <>
                <Button variant="destructive" disabled={submittedByMe} onClick={() => setDialog('reject')}>
                  Reject
                </Button>
                <Button disabled={submittedByMe || !readyToApprove} onClick={() => setDialog('approve')}>
                  Approve
                </Button>
              </>
            ) : null}
          </>
        }
      />

      {actionError ? <Alert tone="danger" className="mb-4">{errorMessage(actionError)}</Alert> : null}
      {inReview && canApprove && submittedByMe ? (
        <Alert tone="info" className="mb-4" title="Four-eyes rule">
          You submitted this case, so another officer must approve or reject it.
        </Alert>
      ) : null}
      {data.decisionNote ? (
        <Alert tone={data.status === 'APPROVED' ? 'success' : 'warning'} className="mb-4" title="Decision note">
          {data.decisionNote}
        </Alert>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-[2fr_3fr]">
        <Card>
          <CardHeader>
            <CardTitle>Case</CardTitle>
          </CardHeader>
          <CardContent>
            <DetailList
              columns={1}
              items={[
                { label: 'Target tier', value: data.targetTierCode },
                { label: 'Customer KYC status', value: <StatusBadge status={data.customerKycStatus} /> },
                { label: 'Branch', value: nameOf(data.branchId) },
                { label: 'Opened', value: formatDateTime(data.createdAt) },
                { label: 'Submitted', value: formatDateTime(data.submittedAt) },
                { label: 'Decided', value: formatDateTime(data.decidedAt) },
                { label: 'Assigned risk', value: data.assignedRiskLevel ? <StatusBadge status={data.assignedRiskLevel} /> : null },
              ]}
            />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Requirements for {data.targetTierCode}</CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="grid gap-2" aria-label="Requirements">
              {data.requirements.map((requirement) => {
                const met = inReview || data.status === 'APPROVED' ? requirement.metForApproval : requirement.metForSubmission;
                return (
                  <li key={requirement.code} className="flex items-start gap-2 text-sm">
                    {met ? <CircleCheck className="mt-0.5 size-4 text-success" aria-label="Met" /> : <CircleDashed className="mt-0.5 size-4 text-muted-foreground" aria-label="Not met" />}
                    <span>
                      {requirement.description}
                      {requirement.metForSubmission && !requirement.metForApproval ? (
                        <span className="text-xs text-muted-foreground"> — captured; needs reviewer acceptance</span>
                      ) : null}
                    </span>
                  </li>
                );
              })}
            </ul>
          </CardContent>
        </Card>
      </div>

      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Checks</CardTitle>
        </CardHeader>
        <CardContent>
          {data.checks.length === 0 ? (
            <EmptyState title="No checks recorded yet" />
          ) : (
            <ul className="divide-y">
              {data.checks.map((check) => (
                <li key={check.id} className="flex flex-wrap items-start justify-between gap-2 py-3 text-sm">
                  <div className="grid gap-0.5">
                    <p className="flex items-center gap-2 font-medium">
                      {humanize(check.checkType)} <StatusBadge status={check.result} />
                    </p>
                    <p className="text-muted-foreground">
                      {humanize(check.method)}
                      {check.provider ? ` · ${check.provider}` : ''}
                      {check.providerReference ? ` · ref ${check.providerReference}` : ''}
                      {check.score !== null ? ` · score ${check.score}` : ''}
                    </p>
                    {check.note ? <p className="text-xs text-muted-foreground">{check.note}</p> : null}
                  </div>
                  <span className="text-xs text-muted-foreground">{formatDateTime(check.performedAt)}</span>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>

      <SignUpAnswers customerId={data.customerId} />

      {dialog ? <DecisionDialog kind={dialog} kycCase={data} onClose={() => setDialog(null)} onDone={updated} /> : null}
    </>
  );
}

const DIALOGS: Record<DecisionKind, { title: string; description: string; confirm: string; destructive?: boolean }> = {
  approve: {
    title: 'Approve KYC',
    description: 'The customer becomes verified at the target tier. Choose the risk rating from your assessment.',
    confirm: 'Approve',
  },
  reject: { title: 'Reject KYC', description: 'The customer is marked as rejected. Explain why.', confirm: 'Reject', destructive: true },
  return: {
    title: 'Return for correction',
    description: 'The case goes back to whoever captured it with your note. A customer who signed up in the app sees the note there, so write it to them.',
    confirm: 'Return case',
  },
  cancel: { title: 'Cancel case', description: 'The case is closed without a decision.', confirm: 'Cancel case', destructive: true },
  check: { title: 'Record manual check', description: 'Record the outcome of a check you performed (e.g. a watchlist or PEP screening).', confirm: 'Record check' },
};

function DecisionDialog({ kind, kycCase, onClose, onDone }: { kind: DecisionKind; kycCase: KycCase; onClose: () => void; onDone: (next: KycCase) => void }) {
  const [note, setNote] = useState('');
  const [riskLevel, setRiskLevel] = useState('LOW');
  const [checkType, setCheckType] = useState('WATCHLIST');
  const [result, setResult] = useState('PASS');
  const meta = DIALOGS[kind];
  const noteRequired = kind !== 'approve' && kind !== 'check';

  const decide = useMutation({
    mutationFn: () => {
      const trimmed = note.trim();
      switch (kind) {
        case 'approve':
          return bff<KycCase>(`/kyc/cases/${kycCase.id}/approve`, { method: 'POST', body: { riskLevel, note: trimmed || undefined, version: kycCase.version } });
        case 'check':
          return bff<KycCase>(`/kyc/cases/${kycCase.id}/checks`, { method: 'POST', body: { checkType, result, note: trimmed || undefined } });
        default:
          return bff<KycCase>(`/kyc/cases/${kycCase.id}/${kind}`, { method: 'POST', body: { note: trimmed, version: kycCase.version } });
      }
    },
    onSuccess: (next) => {
      onDone(next);
      onClose();
    },
  });

  return (
    <Modal
      open
      onClose={onClose}
      busy={decide.isPending}
      title={meta.title}
      description={meta.description}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={decide.isPending}>
            Back
          </Button>
          <Button variant={meta.destructive ? 'destructive' : 'primary'} loading={decide.isPending} disabled={noteRequired && note.trim() === ''} onClick={() => decide.mutate()}>
            {meta.confirm}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {decide.isError ? <Alert tone="danger">{errorMessage(decide.error)}</Alert> : null}
        {kind === 'approve' ? (
          <FormField label="Risk rating" required hint="A failed watchlist or PEP check forces HIGH regardless of this choice.">
            {(control) => (
              <Select {...control} value={riskLevel} onChange={(event) => setRiskLevel(event.target.value)}>
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
              </Select>
            )}
          </FormField>
        ) : null}
        {kind === 'check' ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <FormField label="Check" required>
              {(control) => (
                <Select {...control} value={checkType} onChange={(event) => setCheckType(event.target.value)}>
                  {['WATCHLIST', 'PEP', 'IDENTITY_VERIFICATION', 'FACE_MATCH', 'ADDRESS', 'PHONE', 'DOCUMENT'].map((type) => (
                    <option key={type} value={type}>
                      {humanize(type)}
                    </option>
                  ))}
                </Select>
              )}
            </FormField>
            <FormField label="Result" required>
              {(control) => (
                <Select {...control} value={result} onChange={(event) => setResult(event.target.value)}>
                  <option value="PASS">Pass</option>
                  <option value="FAIL">Fail</option>
                  <option value="INCONCLUSIVE">Inconclusive</option>
                </Select>
              )}
            </FormField>
          </div>
        ) : null}
        <FormField label="Note" required={noteRequired}>
          {(control) => <Textarea {...control} value={note} maxLength={500} onChange={(event) => setNote(event.target.value)} />}
        </FormField>
      </div>
    </Modal>
  );
}
