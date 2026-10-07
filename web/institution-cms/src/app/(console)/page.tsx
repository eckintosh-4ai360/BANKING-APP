'use client';

import { Permission } from '@banking/api';
import { Card, CardContent, CardHeader, CardTitle, PageHeader, Skeleton, StatCard } from '@banking/ui';
import Link from 'next/link';
import { useCan, useMe } from '@/lib/me';
import { useCount } from '@/lib/queries';

function Figure({ value, loading }: { value: number | undefined; loading: boolean }) {
  if (loading) {
    return <Skeleton className="h-8 w-16" />;
  }
  return <>{value === undefined ? '—' : value.toLocaleString()}</>;
}

export default function DashboardPage() {
  const me = useMe();
  const canCustomers = useCan(Permission.customerView);
  const canKyc = useCan(Permission.kycView);
  const canBranches = useCan(Permission.branchView);
  const canStaff = useCan(Permission.staffView);

  const customers = useCount('/customers', {}, canCustomers);
  const pendingCustomers = useCount('/customers', { status: 'PENDING' }, canCustomers);
  const reviews = useCount('/kyc/cases', { status: 'PENDING_REVIEW' }, canKyc);
  const branches = useCount('/branches', { status: 'ACTIVE' }, canBranches);
  const staff = useCount('/staff', { status: 'ACTIVE' }, canStaff);

  const figures = [
    canCustomers && { label: 'Customers', value: customers, hint: 'In your branch scope', href: '/customers' },
    canCustomers && { label: 'Awaiting onboarding', value: pendingCustomers, hint: 'Customers not yet active', href: '/customers?status=PENDING' },
    canKyc && { label: 'KYC reviews pending', value: reviews, hint: 'Submitted and waiting for a decision', href: '/kyc' },
    canBranches && { label: 'Active branches', value: branches, href: '/branches' },
    canStaff && { label: 'Active staff', value: staff, href: '/staff' },
  ].filter((figure): figure is Exclude<typeof figure, false> => Boolean(figure));

  return (
    <>
      <PageHeader title={`Welcome, ${me.firstName}`} description={me.institutionName} />
      {figures.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {figures.map((figure) => (
            <Link key={figure.label} href={figure.href} className="rounded-lg focus-visible:outline-2">
              <StatCard label={figure.label} value={<Figure value={figure.value.data} loading={figure.value.isLoading} />} hint={figure.hint} />
            </Link>
          ))}
        </div>
      ) : null}
      <Card className="mt-6">
        <CardHeader>
          <CardTitle>Your access</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-2 text-sm">
          <p>
            Roles: <span className="font-medium">{me.roles.map((role) => role.name).join(', ') || 'none'}</span>
          </p>
          <p>
            Branch scope: <span className="font-medium">{me.allBranchesAccess ? 'All branches' : 'Your home branch'}</span>
          </p>
          <p className="text-muted-foreground">
            Accounts, deposits, loans and teller operations arrive with the banking core (roadmap phase 2 onwards).
          </p>
        </CardContent>
      </Card>
    </>
  );
}
