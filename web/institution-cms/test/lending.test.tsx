import type { LoanApplicationDetail, LoanDetail, Me } from '@banking/api';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApplicationDialog } from '@/components/loan/application-dialog';
import { LoanDialog } from '@/components/loan/loan-dialog';
import { MeProvider } from '@/lib/me';
import { visibleNavigation } from '@/lib/navigation';

function staff(permissions: string[]): Me {
  return {
    id: 'staff-2',
    tenantId: 'tenant-1',
    tenantCode: 'demo-mfi',
    institutionName: 'Demo MFI',
    username: 'reviewer',
    firstName: 'Efua',
    lastName: 'Mensah',
    email: 'efua@example.test',
    homeBranchId: 'branch-1',
    allBranchesAccess: false,
    roles: [],
    permissions,
    passwordChangeRequired: false,
  };
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function wrap(me: Me, children: ReactNode) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
      <MeProvider me={me}>{children}</MeProvider>
    </QueryClientProvider>
  );
}

const application: LoanApplicationDetail = {
  application: {
    id: 'app-1',
    applicationNumber: 'LAP-20270301-ABC',
    customerId: 'customer-1',
    customerName: 'Akosua Mensah',
    branchId: 'branch-1',
    productId: 'product-1',
    productCode: 'BIZ01',
    productName: 'Business loan',
    productVersionId: 'version-1',
    currency: 'GHS',
    requestedAmount: '1200.00',
    requestedInstallments: 6,
    purpose: 'Restock the shop',
    monthlyIncome: null,
    monthlyExpenses: null,
    existingDebt: null,
    disbursementAccountId: 'account-1',
    status: 'ASSESSED',
    riskRating: 'LOW',
    assessmentNote: 'Steady trade',
    approvedAmount: null,
    approvedInstallments: null,
    firstDueDate: null,
    loanOfficerId: 'staff-1',
    loanOfficerName: 'Test lender',
    secondApprovalRequired: false,
    loanId: null,
    createdAt: '2027-03-01T08:00:00Z',
    updatedAt: '2027-03-01T09:00:00Z',
    version: 3,
  },
  steps: [{ type: 'SUBMIT', actorId: 'staff-1', actorName: 'Test lender', occurredAt: '2027-03-01T08:30:00Z', note: null }],
  guarantors: [],
  collateral: [],
  security: { guarantorsRequired: 0, guarantorsVerified: 0, collateralNeeded: '0.00', collateralVerified: '0.00' },
};

const loanDetail: LoanDetail = {
  loan: {
    id: 'loan-1',
    loanNumber: 'LN-20270301-XYZ',
    applicationId: 'app-1',
    customerId: 'customer-1',
    customerName: 'Akosua Mensah',
    branchId: 'branch-1',
    productVersionId: 'version-1',
    productCode: 'BIZ01',
    productName: 'Business loan',
    repaymentAccountId: 'account-1',
    currency: 'GHS',
    principal: '1200.00',
    interestMethod: 'FLAT',
    annualRate: '24',
    dayCount: 'THIRTY_360',
    repaymentFrequency: 'MONTHLY',
    installments: 6,
    processingFee: '29.00',
    disbursementDate: '2027-03-01',
    firstDueDate: '2027-04-01',
    maturityDate: '2027-09-01',
    status: 'ACTIVE',
    daysPastDue: 0,
    delinquencyBand: 'CURRENT',
    nonAccrual: false,
    principalOutstanding: '1000.00',
    interestReceivable: '12.00',
    penaltyReceivable: '0.00',
    arrears: '0.00',
    nextDueDate: '2027-05-01',
    nextDueAmount: '224.00',
    provisionHeld: '10.00',
    scheduleVersion: 1,
    bandFloor: null,
    bandFloorUntil: null,
    writtenOff: '0.00',
    recovered: '0.00',
    closedOn: null,
    version: 4,
  },
  schedule: [],
  repayments: [],
  payoff: { asOf: '2027-04-16', principal: '1000.00', interest: '12.00', penalty: '0.00', total: '1012.00', interestWaived: '108.00' },
  restructures: [],
  recoveries: [],
};

afterEach(() => vi.unstubAllGlobals());

describe('lending navigation', () => {
  it('shows lending to loan staff and loan products to product staff', () => {
    const hrefs = (permissions: string[]) => visibleNavigation(permissions, '/').flatMap((group) => group.items.map((item) => item.href));
    expect(hrefs(['loan.view', 'loan.create'])).toEqual(expect.arrayContaining(['/loan-applications', '/loans', '/collections']));
    expect(hrefs(['loan.view'])).not.toContain('/loan-products');
    expect(hrefs(['product.manage'])).toContain('/loan-products');
    expect(hrefs(['teller.operate'])).not.toContain('/loans');
  });
});

describe('ApplicationDialog', () => {
  it('recommends with the version it read, and shows the separation-of-duties refusal', async () => {
    const posted: unknown[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init: RequestInit) => {
        if (url.endsWith('/loan-applications/app-1/recommend')) {
          posted.push(JSON.parse(String(init.body)));
          return json(403, { success: false, code: 'SEPARATION_OF_DUTIES', message: 'The loan officer cannot recommend their own application.' });
        }
        if (url.endsWith('/loan-applications/app-1')) {
          return json(200, { success: true, data: application });
        }
        throw new Error(`Unexpected request ${url}`);
      }),
    );
    const user = userEvent.setup();
    render(wrap(staff(['loan.view', 'loan.recommend']), <ApplicationDialog applicationId="app-1" onClose={() => {}} />));

    expect(await screen.findByText('LAP-20270301-ABC · Akosua Mensah')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Approve' })).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Recommend' }));

    expect(await screen.findByText('The loan officer cannot recommend their own application.')).toBeTruthy();
    expect(posted).toEqual([{ version: 3 }]);
  });
});

describe('LoanDialog', () => {
  it('takes the payoff amount exactly as the server quoted it, with an idempotency key', async () => {
    const requests: { body: unknown; key: string | null }[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init: RequestInit) => {
        if (url.endsWith('/loans/loan-1/repayments')) {
          const headers = new Headers(init.headers);
          requests.push({ body: JSON.parse(String(init.body)), key: headers.get('idempotency-key') });
          return json(201, { success: true, data: { repayment: {}, loan: loanDetail.loan, settled: true } });
        }
        if (url.endsWith('/loans/loan-1')) {
          return json(200, { success: true, data: loanDetail });
        }
        throw new Error(`Unexpected request ${url}`);
      }),
    );
    const user = userEvent.setup();
    render(wrap(staff(['loan.view', 'loan.repay']), <LoanDialog loanId="loan-1" initialTab="actions" onClose={() => {}} />));

    await user.click(await screen.findByRole('button', { name: /Pay off/ }));
    await user.click(screen.getByRole('button', { name: 'Take repayment' }));

    expect(await screen.findByText('The loan is repaid in full.')).toBeTruthy();
    await waitFor(() => expect(requests).toHaveLength(1));
    expect(requests[0]?.body).toEqual({ amount: '1012.00', source: 'ACCOUNT' });
    expect(requests[0]?.key).toMatch(/^[0-9a-f-]{36}$/);
    expect(screen.queryByRole('button', { name: 'Ask to write off' })).toBeNull();
  });
});
