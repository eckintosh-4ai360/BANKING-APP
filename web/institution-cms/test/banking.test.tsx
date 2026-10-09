import type { Account, Charge, Me } from '@banking/api';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MovementDialog } from '@/components/account/movement-dialog';
import { formDefaults, toTermsRequest } from '@/components/product/product-form';
import { fileNameOf } from '@/lib/download';
import { MeProvider } from '@/lib/me';
import { visibleNavigation } from '@/lib/navigation';
import { amountText, movementSchema, productFormSchema } from '@/lib/schemas';

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
  permissions: ['account.view', 'transaction.view', 'transaction.create'],
  passwordChangeRequired: false,
};

const account: Account = {
  id: 'acc-1',
  accountNumber: '1000000013',
  title: 'Ama Mensah',
  customerId: 'cust-1',
  productId: 'prod-1',
  productCode: 'SAVE01',
  productName: 'Savings',
  productType: 'SAVINGS',
  productVersionId: 'ver-1',
  branchId: 'branch-1',
  currency: 'GHS',
  status: 'ACTIVE',
  statusReason: null,
  ownershipType: 'SINGLE',
  holders: [],
  ledgerBalance: '500.00',
  holdAmount: '0.00',
  availableBalance: '500.00',
  overdraftLimit: '0.00',
  openedOn: '2026-10-01',
  activatedAt: null,
  closedOn: null,
  lastActivityAt: null,
  version: 0,
};

afterEach(() => vi.unstubAllGlobals());

describe('banking navigation', () => {
  it('shows accounts to tellers and approvals only to reviewers', () => {
    const teller = visibleNavigation(['account.view'], '/accounts/1').flatMap((group) => group.items);
    expect(teller.map((item) => item.label)).toContain('Accounts');
    expect(teller.map((item) => item.label)).not.toContain('Approvals');
    expect(teller.find((item) => item.href === '/accounts')?.active).toBe(true);

    const manager = visibleNavigation(['approval.view', 'product.view'], '/').flatMap((group) => group.items.map((item) => item.label));
    expect(manager).toEqual(expect.arrayContaining(['Approvals', 'Products']));
  });
});

describe('amounts', () => {
  it('accepts plain decimals and keeps them as typed', () => {
    expect(amountText.parse(' 1250.00 ')).toBe('1250.00');
    expect(amountText.parse('0.5')).toBe('0.5');
    expect(amountText.parse('999999999999999.99')).toBe('999999999999999.99');
  });

  it.each(['1,250.00', '0.00', '-5', '1e3', '12.', '.5', '01', 'abc', ''])('rejects %j', (value) => {
    expect(amountText.safeParse(value).success).toBe(false);
  });

  it('asks for the receiving account only on transfers', () => {
    const values = { amount: '10.00', narration: '', externalReference: '', toAccountNumber: '' };
    expect(movementSchema(false).safeParse(values).success).toBe(true);
    const transfer = movementSchema(true).safeParse(values);
    expect(transfer.error?.issues[0]?.path).toEqual(['toAccountNumber']);
    expect(movementSchema(true).safeParse({ ...values, toAccountNumber: '1000000021' }).success).toBe(true);
  });
});

describe('product terms', () => {
  const percent: Charge = {
    event: 'CASH_DEPOSIT',
    name: 'Deposit fee',
    calculation: 'PERCENT',
    flatAmount: null,
    rate: '0.5',
    minAmount: '1.00',
    maxAmount: null,
  };

  it('sends flat fees from the form, keeps other charges and never turns amounts into numbers', () => {
    const values = productFormSchema.parse({
      ...formDefaults(),
      code: 'save01',
      name: 'Savings',
      minOpeningBalance: '50.00',
      withdrawalFee: '2.50',
      transferFee: '',
    });
    const terms = toTermsRequest(values, [percent]);
    expect(values.code).toBe('SAVE01');
    expect(terms.minOpeningBalance).toBe('50.00');
    expect(terms.maxBalance).toBeUndefined();
    expect(terms.charges).toEqual([
      { event: 'CASH_WITHDRAWAL', name: 'Withdrawal fee', calculation: 'FLAT', flatAmount: '2.50', rate: null, minAmount: null, maxAmount: null },
      percent,
    ]);
  });

  it('does not overwrite a percentage withdrawal charge with a flat one', () => {
    const percentWithdrawal: Charge = { ...percent, event: 'CASH_WITHDRAWAL', name: 'Withdrawal fee' };
    const values = productFormSchema.parse({ ...formDefaults(), code: 'S1X', name: 'Savings', withdrawalFee: '9.00' });
    expect(toTermsRequest(values, [percentWithdrawal]).charges).toEqual([percentWithdrawal]);
  });

  it('shows zero amounts as empty fields', () => {
    const defaults = formDefaults(undefined, {
      minOpeningBalance: '0.00',
      minOperatingBalance: '25.00',
      maxBalance: null,
      interestRate: '0',
      charges: [],
      currency: 'GHS',
    } as never);
    expect(defaults.minOpeningBalance).toBe('');
    expect(defaults.minOperatingBalance).toBe('25.00');
    expect(defaults.interestRate).toBe('');
  });
});

describe('downloads', () => {
  it('takes the server file name but only safe characters of it', () => {
    expect(fileNameOf('attachment; filename="statement-1000000013-2026-10-01-2026-10-31.pdf"')).toBe(
      'statement-1000000013-2026-10-01-2026-10-31.pdf',
    );
    expect(fileNameOf('attachment; filename="../../etc/passwd"')).toBe('.._.._etc_passwd');
    expect(fileNameOf(null)).toBeUndefined();
  });
});

describe('MovementDialog', () => {
  it('retries with the same idempotency key so the money moves at most once', async () => {
    const calls: Headers[] = [];
    const responses = [
      new Response(JSON.stringify({ success: false, code: 'SERVICE_UNAVAILABLE', message: 'Try again' }), { status: 503 }),
      new Response(
        JSON.stringify({ success: true, data: { outcome: 'POSTED', transaction: null, balances: [], approval: null } }),
        { status: 201 },
      ),
    ];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: string, init: RequestInit) => {
        calls.push(new Headers(init.headers));
        return responses.shift() as Response;
      }),
    );
    const onDone = vi.fn();
    const user = userEvent.setup();
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
        <MeProvider me={me}>
          <MovementDialog kind="deposit" account={account} onClose={() => undefined} onDone={onDone} />
        </MeProvider>
      </QueryClientProvider>,
    );

    await user.type(screen.getByLabelText(/Amount/), '120.50');
    await user.click(screen.getByRole('button', { name: 'Post deposit' }));
    await screen.findByText(/Try again|went wrong/);
    await user.click(screen.getByRole('button', { name: 'Post deposit' }));

    await waitFor(() => expect(onDone).toHaveBeenCalledOnce());
    expect(calls).toHaveLength(2);
    const firstKey = calls[0]?.get('idempotency-key');
    expect(firstKey).toMatch(/^[A-Za-z0-9_-]{8,100}$/);
    expect(calls[1]?.get('idempotency-key')).toBe(firstKey);
  });
});
