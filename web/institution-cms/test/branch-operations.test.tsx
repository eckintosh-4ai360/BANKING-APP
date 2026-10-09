import type { Me } from '@banking/api';
import { AuditSealsView } from '@banking/console';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import TellerPage from '@/app/(console)/teller/page';
import { countTotal, isValidCount, toCashCount } from '@/lib/cash';
import { MeProvider } from '@/lib/me';
import { visibleNavigation } from '@/lib/navigation';

const me: Me = {
  id: 'staff-1',
  tenantId: 'tenant-1',
  tenantCode: 'demo-mfi',
  institutionName: 'Demo MFI',
  username: 'teller',
  firstName: 'Ama',
  lastName: 'Mensah',
  email: 'ama@example.test',
  homeBranchId: 'branch-1',
  allBranchesAccess: false,
  roles: [{ id: 'r1', code: 'TELLER', name: 'Teller' }],
  permissions: ['account.view', 'transaction.create', 'teller.operate', 'cash.view'],
  passwordChangeRequired: false,
};

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

function wrap(children: ReactNode) {
  return (
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>
      <MeProvider me={me}>{children}</MeProvider>
    </QueryClientProvider>
  );
}

afterEach(() => vi.unstubAllGlobals());

describe('cash counts', () => {
  it('adds notes and coins exactly, without floating point', () => {
    expect(countTotal({ '200': '3', '0.50': '3', '0.01': '1' })).toBe('601.51');
    expect(countTotal({ '0.10': '3', '0.20': '1' })).toBe('0.50');
    expect(countTotal({ '0.05': '99999999' })).toBe('4999999.95');
    expect(countTotal({})).toBe('0.00');
  });

  it('sends only counted denominations and never a total', () => {
    expect(toCashCount({ '200': '2', '100': '', '50': '0', '1': ' 7 ' })).toEqual({ denominations: { '200': 2, '1': 7 } });
    expect(isValidCount({ '200': '2', '100': '' })).toBe(true);
    expect(isValidCount({ '200': '1.5' })).toBe(false);
    expect(isValidCount({ '200': '-1' })).toBe(false);
  });
});

describe('branch operations navigation', () => {
  it('shows the till and cash to tellers and end-of-day only to operations staff', () => {
    const teller = visibleNavigation(me.permissions, '/teller').flatMap((group) => group.items);
    expect(teller.map((item) => item.href)).toEqual(expect.arrayContaining(['/teller', '/cash']));
    expect(teller.find((item) => item.href === '/teller')?.active).toBe(true);
    expect(teller.map((item) => item.href)).not.toContain('/operations');

    const operations = visibleNavigation(['operations.view'], '/operations').flatMap((group) => group.items);
    expect(operations.map((item) => item.href)).toEqual(['/', '/operations', '/account']);
  });
});

describe('TellerPage', () => {
  it('opens a till on a free drawer with a note-by-note count', async () => {
    const posted: unknown[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init: RequestInit) => {
        if (url.endsWith('/teller/sessions/me')) {
          return json(404, { success: false, code: 'RESOURCE_NOT_FOUND', message: 'Open teller session not found' });
        }
        if (url.includes('/cash/drawers')) {
          return json(200, {
            success: true,
            data: [
              { id: 'd1', branchId: 'branch-1', currency: 'GHS', code: 'T1', name: 'Till 1', status: 'ACTIVE', balance: '200.00', sessionId: null, tellerId: null, version: 0 },
              { id: 'd2', branchId: 'branch-1', currency: 'GHS', code: 'T2', name: 'Till 2', status: 'ACTIVE', balance: '0.00', sessionId: 's9', tellerId: 'staff-9', version: 0 },
            ],
          });
        }
        if (url.endsWith('/teller/sessions') && init.method === 'POST') {
          posted.push(JSON.parse(String(init.body)));
          return json(422, { success: false, code: 'OPENING_COUNT_MISMATCH', message: 'The count does not match the drawer.' });
        }
        throw new Error(`Unexpected request ${url}`);
      }),
    );
    const user = userEvent.setup();
    render(wrap(<TellerPage />));

    const drawer = await screen.findByLabelText(/^Drawer/);
    await waitFor(() => expect(screen.getAllByRole('option')).toHaveLength(2));
    expect(screen.queryByRole('option', { name: /T2/ })).toBeNull();
    await user.selectOptions(drawer, 'd1');
    await user.type(screen.getByLabelText('GHS 100'), '2');
    expect(screen.getByText(/Total counted/).textContent).toMatch(/200\.00/);
    await user.click(screen.getByRole('button', { name: 'Open till' }));

    await screen.findByText('The count does not match the drawer.');
    expect(posted).toEqual([{ drawerId: 'd1', openingCount: { denominations: { '100': 2 } } }]);
  });
});

describe('AuditSealsView', () => {
  it('reports what changed after the trail was sealed', async () => {
    const urls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        urls.push(url);
        if (url.includes('/verification')) {
          return json(200, {
            success: true,
            data: {
              from: '2027-04-01T00:00:00Z',
              to: '2027-04-02T00:00:00Z',
              sealsChecked: 24,
              rowsChecked: 310,
              sealedThrough: '2027-04-02T00:00:00Z',
              intact: false,
              problems: [{ sequenceNo: 7, rangeStart: '2027-04-01T06:00:00Z', rangeEnd: '2027-04-01T07:00:00Z', code: 'ROWS_CHANGED', detail: '12 rows were sealed, 11 are there now' }],
            },
          });
        }
        return json(200, { success: true, data: { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 } });
      }),
    );
    const user = userEvent.setup();
    render(wrap(<AuditSealsView endpoint="/audit-seals" />));

    fireEvent.change(screen.getByLabelText(/Check from/), { target: { value: '2027-04-01T00:00' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2027-04-02T00:00' } });
    await user.click(screen.getByRole('button', { name: 'Verify' }));

    await screen.findByText('The audit trail was changed after it was sealed');
    expect(screen.getByText(/Seal #7/).textContent).toContain('12 rows were sealed, 11 are there now');
    expect(urls.find((url) => url.includes('/verification'))).toMatch(/\/api\/bff\/audit-seals\/verification\?from=.+&to=.+/);
  });
});
