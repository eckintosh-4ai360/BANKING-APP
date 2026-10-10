import type { ChannelSettings, CustomerSignUp, Me } from '@banking/api';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CustomerAppSettings } from '@/components/channel/customer-app-settings';
import { SignUpAnswers } from '@/components/channel/sign-up-answers';
import { MeProvider } from '@/lib/me';

function staff(permissions: string[]): Me {
  return {
    id: 'staff-1',
    tenantId: 'tenant-1',
    tenantCode: 'demo-mfi',
    institutionName: 'Demo MFI',
    username: 'admin',
    firstName: 'Adwoa',
    lastName: 'Asante',
    email: 'adwoa@example.test',
    homeBranchId: 'branch-1',
    allBranchesAccess: true,
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

const settings: ChannelSettings = {
  currency: 'GHS',
  maxTransferAmount: '5000.00',
  dailyTransferLimit: '10000.00',
  beneficiaryCooldownHours: 24,
  cooldownMaxAmount: '1000.00',
  newDeviceCooldownHours: 24,
  newDeviceMaxAmount: '1000.00',
  onboarding: { enabled: false, branchId: null, branchName: null, tierCode: null, productId: null, productName: null, minimumAge: 18 },
  updatedAt: '2027-03-01T08:00:00Z',
  version: 4,
};

const branches = { items: [{ id: 'branch-1', code: 'HO', name: 'Head office', status: 'ACTIVE' }], page: 0, size: 100, totalItems: 1, totalPages: 1 };
const tiers = [
  { code: 'TIER_1', name: 'Basic', tierRank: 1, active: true },
  { code: 'TIER_2', name: 'Standard', tierRank: 2, active: true },
];
const products = [
  { id: 'product-1', code: 'SAV01', name: 'Regular savings', productType: 'SAVINGS', status: 'ACTIVE', currentVersion: { currency: 'GHS' } },
  { id: 'product-2', code: 'SUSU1', name: 'Daily susu', productType: 'SUSU', status: 'ACTIVE', currentVersion: { currency: 'GHS' } },
  { id: 'product-3', code: 'USD01', name: 'Dollar savings', productType: 'SAVINGS', status: 'ACTIVE', currentVersion: { currency: 'USD' } },
];

afterEach(() => vi.unstubAllGlobals());

describe('CustomerAppSettings', () => {
  it('turns sign-up on with a branch, a tier and a savings or current product in the base currency', async () => {
    const saved: unknown[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init: RequestInit) => {
        if (url.endsWith('/channel-settings/onboarding')) {
          saved.push(JSON.parse(String(init.body)));
          return json(200, { success: true, data: { ...settings, onboarding: { enabled: true, branchId: 'branch-1', branchName: 'Head office', tierCode: 'TIER_2', productId: 'product-1', productName: 'Regular savings', minimumAge: 18 }, version: 5 } });
        }
        if (url.endsWith('/channel-settings')) {
          return json(200, { success: true, data: settings });
        }
        if (url.includes('/branches')) {
          return json(200, { success: true, data: branches });
        }
        if (url.endsWith('/kyc/tiers')) {
          return json(200, { success: true, data: tiers });
        }
        if (url.endsWith('/products')) {
          return json(200, { success: true, data: products });
        }
        throw new Error(`Unexpected request ${url}`);
      }),
    );
    const user = userEvent.setup();
    render(wrap(staff(['settings.view', 'settings.manage', 'branch.view', 'product.view', 'kyc.view']), <CustomerAppSettings />));

    expect(await screen.findByText('Transfer limits')).toBeTruthy();
    expect(screen.getByText('Off')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Edit sign-up' }));
    await user.click(screen.getByLabelText('Let people sign up in the app'));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Choose the branch new customers belong to')).toBeTruthy();
    expect(saved).toHaveLength(0);

    const productSelect = screen.getByLabelText('First account');
    expect([...productSelect.querySelectorAll('option')].map((option) => option.textContent)).toEqual(['Choose a product', 'Regular savings (SAV01)']);
    await user.selectOptions(await screen.findByLabelText('Branch for new customers'), 'branch-1');
    await user.selectOptions(screen.getByLabelText(/KYC tier/), 'TIER_2');
    await user.selectOptions(productSelect, 'product-1');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(saved).toHaveLength(1));
    expect(saved[0]).toEqual({ enabled: true, branchId: 'branch-1', tierCode: 'TIER_2', productId: 'product-1', minimumAge: 18, version: 4 });
    expect(await screen.findByText('On')).toBeTruthy();
    expect(screen.getByText('Regular savings')).toBeTruthy();
  });

  it('sends transfer limits as exact strings with the version it read', async () => {
    const saved: unknown[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init: RequestInit) => {
        if (url.endsWith('/channel-settings') && init?.method === 'PUT') {
          saved.push(JSON.parse(String(init.body)));
          return json(422, { success: false, code: 'BUSINESS_RULE_VIOLATION', message: 'The daily limit must cover one transfer.' });
        }
        if (url.endsWith('/channel-settings')) {
          return json(200, { success: true, data: settings });
        }
        throw new Error(`Unexpected request ${url}`);
      }),
    );
    const user = userEvent.setup();
    render(wrap(staff(['settings.view', 'settings.manage']), <CustomerAppSettings />));

    await user.click(await screen.findByRole('button', { name: 'Edit limits' }));
    const perTransfer = screen.getByLabelText(/^One transfer\*?$/);
    await user.clear(perTransfer);
    await user.type(perTransfer, '20000.10');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('The daily limit must cover one transfer.')).toBeTruthy();
    expect(saved).toEqual([
      {
        maxTransferAmount: '20000.10',
        dailyTransferLimit: '10000.00',
        beneficiaryCooldownHours: 24,
        cooldownMaxAmount: '1000.00',
        newDeviceCooldownHours: 24,
        newDeviceMaxAmount: '1000.00',
        version: 4,
      },
    ]);
  });

  it('shows the limits without edit buttons to those who may only view', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(200, { success: true, data: settings })),
    );
    render(wrap(staff(['settings.view']), <CustomerAppSettings />));

    expect(await screen.findByText('Transfer limits')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Edit limits' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Edit sign-up' })).toBeNull();
  });
});

describe('SignUpAnswers', () => {
  const answers: CustomerSignUp = {
    customerId: 'customer-1',
    startedAt: '2027-03-01T08:00:00Z',
    sourceOfFunds: 'TRADING',
    accountPurpose: 'SAVINGS',
    expectedMonthlyTurnover: 'UP_TO_5000',
    politicallyExposed: true,
    riskAnsweredAt: '2027-03-01T08:20:00Z',
    kycCaseId: 'case-1',
    submittedAt: '2027-03-01T08:30:00Z',
    accountId: null,
    completedAt: null,
  };

  it('flags a declared politically exposed person to the reviewer', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(200, { success: true, data: answers })),
    );
    render(wrap(staff(['kyc.view', 'customer.view']), <SignUpAnswers customerId="customer-1" />));

    expect(await screen.findByText('Declared politically exposed')).toBeTruthy();
    expect(screen.getByText('Up to 5,000')).toBeTruthy();
    expect(screen.getByText('Trading')).toBeTruthy();
  });

  it('shows nothing for a customer onboarded at a branch', async () => {
    const fetch = vi.fn(async () => json(404, { success: false, code: 'RESOURCE_NOT_FOUND', message: 'Sign-up not found' }));
    vi.stubGlobal('fetch', fetch);
    const { container } = render(wrap(staff(['kyc.view', 'customer.view']), <SignUpAnswers customerId="customer-2" />));

    await waitFor(() => expect(fetch).toHaveBeenCalled());
    await waitFor(() => expect(container.textContent).toBe(''));
  });
});
