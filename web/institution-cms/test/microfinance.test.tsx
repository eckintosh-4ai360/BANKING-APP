import type { Me } from '@banking/api';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import SusuPage from '@/app/(console)/susu/page';
import { AlertsPanel } from '@/components/field/alerts-panel';
import { MeProvider } from '@/lib/me';
import { visibleNavigation } from '@/lib/navigation';

const supervisor: Me = {
  id: 'staff-1',
  tenantId: 'tenant-1',
  tenantCode: 'demo-mfi',
  institutionName: 'Demo MFI',
  username: 'manager',
  firstName: 'Kwame',
  lastName: 'Boateng',
  email: 'kwame@example.test',
  homeBranchId: 'branch-1',
  allBranchesAccess: false,
  roles: [{ id: 'r1', code: 'BRANCH_MANAGER', name: 'Branch Manager' }],
  permissions: ['field.manage', 'collection.view', 'susu.view', 'susu.manage'],
  passwordChangeRequired: false,
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function page(items: unknown[]) {
  return { success: true, data: { items, page: 0, size: 20, totalItems: items.length, totalPages: 1 } };
}

function wrap(children: ReactNode) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
      <MeProvider me={supervisor}>{children}</MeProvider>
    </QueryClientProvider>
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('microfinance navigation', () => {
  it('shows field operations to supervisors and susu to susu staff, not to tellers', () => {
    const hrefs = visibleNavigation(supervisor.permissions, '/field').flatMap((group) => group.items.map((item) => item.href));
    expect(hrefs).toEqual(expect.arrayContaining(['/field', '/susu']));
    const teller = visibleNavigation(['teller.operate', 'cash.view'], '/').flatMap((group) => group.items.map((item) => item.href));
    expect(teller).not.toContain('/field');
    expect(teller).not.toContain('/susu');
  });
});

describe('AlertsPanel', () => {
  it('resolves an alert with the supervisor’s finding and the version it read', async () => {
    const posted: unknown[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init: RequestInit) => {
        if (url.endsWith('/field/officers')) {
          return json(200, { success: true, data: [{ staffId: 'officer-1', firstName: 'Abena', lastName: 'Owusu', branchId: 'branch-1', currency: 'GHS', status: 'ACTIVE', dailyTarget: null, maxOfflineAmount: '1000.00', maxOfflineHours: 24, cashBalance: '80.00', assignedCustomers: 4, openAlerts: 1, createdAt: '2027-03-01T08:00:00Z', version: 0 }] });
        }
        if (url.includes('/field/alerts/alert-1/resolve')) {
          posted.push(JSON.parse(String(init.body)));
          return json(200, { success: true, data: {} });
        }
        if (url.includes('/field/alerts')) {
          return json(200, page([{ id: 'alert-1', officerId: 'officer-1', deviceId: 'device-1', alertType: 'SEQUENCE_GAP', detail: 'Collections 3 to 5 of the device never arrived', missingFrom: 3, missingTo: 5, clientReference: null, status: 'OPEN', raisedAt: '2027-03-01T09:00:00Z', resolvedAt: null, resolvedBy: null, resolution: null, version: 2 }]));
        }
        throw new Error(`Unexpected request ${url}`);
      }),
    );
    const user = userEvent.setup();
    render(wrap(<AlertsPanel />));

    expect(await screen.findByText('Collections 3 to 5 of the device never arrived')).toBeTruthy();
    expect(await screen.findByText('Abena Owusu')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Resolve' }));
    await user.click(screen.getByLabelText(/What you found/));
    await user.paste('Phone was lost; collections re-entered');
    const dialogButtons = screen.getAllByRole('button', { name: 'Resolve' });
    await user.click(dialogButtons[dialogButtons.length - 1] as HTMLElement);

    await waitFor(() => expect(posted).toEqual([{ note: 'Phone was lost; collections re-entered', version: 2 }]));
  });
});

describe('SusuPage', () => {
  it('lists running plans with their arrears', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        json(200, page([{ id: 'plan-1', planNumber: 'SUSU-20270301-AB12CD34EF56', customerId: 'c1', accountId: 'a1', branchId: 'branch-1', frequencyCode: 'DAILY', contributionAmount: '10.00', currency: 'GHS', cycleLength: 31, commissionContributions: 1, startDate: '2027-03-01', endDate: null, targetAmount: null, status: 'ACTIVE', currentCycle: 1, paid: 4, missed: 2, arrears: '20.00', nextDue: '2027-03-05', totalPaid: '40.00', createdAt: '2027-03-01T08:00:00Z', closedAt: null, closeReason: null, version: 3 }])),
      ),
    );
    render(wrap(<SusuPage />));

    expect(await screen.findByText('SUSU-20270301-AB12CD34EF56')).toBeTruthy();
    expect(screen.getByText(/\(2\)/).textContent).toMatch(/20\.00/);
    expect(screen.getByRole('button', { name: /Open plan/ })).toBeTruthy();
  });
});
