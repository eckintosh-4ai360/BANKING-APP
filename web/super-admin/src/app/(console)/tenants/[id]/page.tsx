'use client';

import { bff, Permission, type FeatureState, type TenantDetails } from '@banking/api';
import { errorMessage } from '@banking/console';
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  DetailList,
  FormField,
  Modal,
  PageHeader,
  Select,
  Skeleton,
  StatusBadge,
  Textarea,
  humanize,
} from '@banking/ui';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { useCan } from '@/lib/me';

export default function TenantPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const canManage = useCan(Permission.platformTenantManage);
  const canLicense = useCan(Permission.platformFeatureManage);
  const [changingStatus, setChangingStatus] = useState(false);
  const key = ['tenant', id];
  const tenant = useQuery({ queryKey: key, queryFn: ({ signal }) => bff<TenantDetails>(`/platform/tenants/${id}`, { signal }) });

  const license = useMutation({
    mutationFn: (feature: FeatureState) => bff<TenantDetails>(`/platform/tenants/${id}/features/${feature.code}`, { method: 'PUT', body: { licensed: !feature.licensed } }),
    onSuccess: (updated) => queryClient.setQueryData(key, updated),
  });

  if (tenant.isPending) {
    return <Skeleton className="h-64" />;
  }
  if (tenant.isError) {
    return <Alert tone="danger">{errorMessage(tenant.error)}</Alert>;
  }
  const { tenant: summary, features } = tenant.data;

  return (
    <>
      <PageHeader
        title={summary.displayName}
        description={
          <span className="flex items-center gap-2">
            <code>{summary.code}</code>
            <StatusBadge status={summary.status} />
          </span>
        }
        actions={
          canManage && summary.status !== 'TERMINATED' ? (
            <Button variant="outline" onClick={() => setChangingStatus(true)}>
              Change status
            </Button>
          ) : null
        }
      />
      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Institution</CardTitle>
          </CardHeader>
          <CardContent>
            <DetailList
              items={[
                { label: 'Legal name', value: summary.legalName },
                { label: 'Type', value: humanize(summary.institutionType) },
                { label: 'Licence number', value: summary.licenceNumber },
                { label: 'Country', value: summary.countryCode },
                { label: 'Base currency', value: summary.baseCurrency },
                { label: 'Time zone', value: summary.timezone },
                { label: 'Locale', value: summary.locale },
              ]}
            />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Licensed features</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-3">
            {license.isError ? <Alert tone="danger">{errorMessage(license.error)}</Alert> : null}
            <p className="text-sm text-muted-foreground">Licensing makes a feature available; the institution decides when to switch it on.</p>
            <ul className="divide-y">
              {features.map((feature) => (
                <li key={feature.code} className="flex items-center justify-between gap-3 py-2">
                  <div className="grid">
                    <span className="flex items-center gap-2 text-sm font-medium">
                      {feature.name}
                      {feature.licensed ? <Badge tone="success">Licensed</Badge> : <Badge>Not licensed</Badge>}
                      {feature.enabled ? <Badge tone="info">On</Badge> : null}
                    </span>
                    <span className="text-xs text-muted-foreground">{feature.description}</span>
                  </div>
                  {canLicense ? (
                    <Button
                      size="sm"
                      variant={feature.licensed ? 'outline' : 'primary'}
                      disabled={license.isPending}
                      loading={license.isPending && license.variables?.code === feature.code}
                      onClick={() => license.mutate(feature)}
                    >
                      {feature.licensed ? 'Revoke' : 'License'}
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      </div>
      {changingStatus ? <StatusDialog tenantId={id} current={summary.status} onClose={() => setChangingStatus(false)} onChanged={() => void queryClient.invalidateQueries({ queryKey: key })} /> : null}
    </>
  );
}

function StatusDialog({ tenantId, current, onClose, onChanged }: { tenantId: string; current: string; onClose: () => void; onChanged: () => void }) {
  const queryClient = useQueryClient();
  const options = ['ACTIVE', 'SUSPENDED', 'TERMINATED'].filter((status) => status !== current);
  const [status, setStatus] = useState(options[0] ?? 'ACTIVE');
  const [reason, setReason] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const change = useMutation({
    mutationFn: () => bff(`/platform/tenants/${tenantId}/status`, { method: 'POST', body: { status, reason: reason.trim() } }),
    onSuccess: () => {
      onChanged();
      void queryClient.invalidateQueries({ queryKey: ['tenants'] });
      onClose();
    },
  });
  const terminating = status === 'TERMINATED';
  return (
    <Modal
      open
      onClose={onClose}
      busy={change.isPending}
      title="Change institution status"
      description="Suspending an institution signs out all of its users and blocks sign-in until it is reactivated."
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={change.isPending}>
            Cancel
          </Button>
          <Button
            variant={status === 'ACTIVE' ? 'primary' : 'destructive'}
            loading={change.isPending}
            disabled={reason.trim().length < 3 || (terminating && confirmation !== 'TERMINATE')}
            onClick={() => change.mutate()}
          >
            Change to {humanize(status)}
          </Button>
        </>
      }
    >
      <div className="grid gap-4">
        {change.isError ? <Alert tone="danger">{errorMessage(change.error)}</Alert> : null}
        <FormField label="New status" required>
          {(control) => (
            <Select {...control} value={status} onChange={(event) => setStatus(event.target.value)}>
              {options.map((option) => (
                <option key={option} value={option}>
                  {humanize(option)}
                </option>
              ))}
            </Select>
          )}
        </FormField>
        <FormField label="Reason" required>
          {(control) => <Textarea {...control} value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} />}
        </FormField>
        {terminating ? (
          <>
            <Alert tone="danger" title="Termination is permanent">
              The institution can no longer be used. Its records are retained for the statutory period.
            </Alert>
            <FormField label='Type "TERMINATE" to confirm' required>
              {(control) => <input {...control} className="h-9 rounded-md border border-input bg-card px-3 text-sm" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} />}
            </FormField>
          </>
        ) : null}
      </div>
    </Modal>
  );
}
