import type { Me } from '@banking/api';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { contentSecurityPolicy } from '@/lib/csp';
import { Can, MeProvider } from '@/lib/me';
import { visibleNavigation } from '@/lib/navigation';
import { createCustomerSchema } from '@/lib/schemas';

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
  permissions: ['customer.view', 'customer.create'],
  passwordChangeRequired: false,
};

describe('navigation', () => {
  it('shows only what the permissions allow', () => {
    const labels = visibleNavigation(['customer.view'], '/customers/abc').flatMap((group) => group.items.map((item) => item.label));
    expect(labels).toEqual(['Dashboard', 'Customers', 'Account security']);
  });

  it('marks the current section active', () => {
    const items = visibleNavigation(['customer.view', 'kyc.view'], '/kyc/123').flatMap((group) => group.items);
    expect(items.find((item) => item.href === '/kyc')?.active).toBe(true);
    expect(items.find((item) => item.href === '/')?.active).toBe(false);
  });

  it('shows administration sections to administrators', () => {
    const labels = visibleNavigation(['branch.view', 'staff.view', 'role.view', 'audit.view', 'institution.view'], '/').flatMap((group) =>
      group.items.map((item) => item.label),
    );
    expect(labels).toEqual(expect.arrayContaining(['Branches', 'Staff', 'Roles & permissions', 'Audit log', 'Settings']));
    expect(labels).not.toContain('Customers');
  });
});

describe('Can', () => {
  it('renders children only with a matching permission', () => {
    render(
      <MeProvider me={me}>
        <Can permission="customer.create">
          <span>create</span>
        </Can>
        <Can permission={['kyc.approve', 'kyc.review']} fallback={<span>no review</span>}>
          <span>review</span>
        </Can>
      </MeProvider>,
    );
    expect(screen.getByText('create')).toBeInTheDocument();
    expect(screen.queryByText('review')).toBeNull();
    expect(screen.getByText('no review')).toBeInTheDocument();
  });
});

describe('contentSecurityPolicy', () => {
  it('allows only nonce-bearing scripts and same-origin connections in production', () => {
    const policy = contentSecurityPolicy('abc123', false);
    expect(policy).toContain("script-src 'self' 'nonce-abc123' 'strict-dynamic'");
    expect(policy).not.toContain('unsafe-eval');
    expect(policy).toContain("connect-src 'self'");
    expect(policy).toContain("frame-ancestors 'none'");
    expect(policy).toContain("object-src 'none'");
    expect(policy).toContain('upgrade-insecure-requests');
  });

  it('relaxes only what development needs', () => {
    const policy = contentSecurityPolicy('abc123', true);
    expect(policy).toContain("'unsafe-eval'");
    expect(policy).not.toContain('upgrade-insecure-requests');
  });
});

describe('createCustomerSchema', () => {
  const base = {
    homeBranchId: '0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b',
    onboardingChannel: 'BRANCH' as const,
    primaryPhone: '',
    email: '',
    preferredLanguage: 'en',
  };
  const individual = {
    title: '',
    firstName: ' Ama ',
    middleName: '',
    lastName: 'Mensah',
    dateOfBirth: '1990-04-12',
    gender: '' as const,
    nationality: 'GH',
    maritalStatus: '' as const,
    occupation: '',
    employerName: '',
    employmentStatus: '' as const,
    monthlyIncomeBand: '',
    taxId: '',
  };

  it('normalises an individual: trims text and turns blanks into nulls for the API', () => {
    const parsed = createCustomerSchema.parse({ ...base, customerType: 'INDIVIDUAL', individual });
    expect(parsed.individual?.firstName).toBe('Ama');
    expect(parsed.individual?.middleName).toBeUndefined();
    expect(parsed.primaryPhone).toBeUndefined();
    expect(parsed.business).toBeUndefined();
  });

  it('requires the section that matches the customer type', () => {
    const result = createCustomerSchema.safeParse({ ...base, customerType: 'BUSINESS', individual });
    expect(result.success).toBe(false);
    expect(result.error?.issues.some((issue) => issue.path.join('.') === 'business')).toBe(true);
  });

  it('rejects a future date of birth, a malformed phone and a bad tax id', () => {
    const result = createCustomerSchema.safeParse({
      ...base,
      primaryPhone: '024-123',
      customerType: 'INDIVIDUAL',
      individual: { ...individual, dateOfBirth: '2999-01-01', taxId: 'abc' },
    });
    const paths = result.error?.issues.map((issue) => issue.path.join('.'));
    expect(paths).toEqual(expect.arrayContaining(['primaryPhone', 'individual.dateOfBirth', 'individual.taxId']));
  });

  it('drops the other section so only one profile is sent', () => {
    const parsed = createCustomerSchema.parse({
      ...base,
      customerType: 'BUSINESS',
      business: {
        registeredName: 'Kofi Traders Ltd',
        tradingName: '',
        registrationNumber: 'CS123456789',
        registrationDate: '',
        businessType: 'LIMITED_COMPANY',
        industrySector: '',
        annualTurnoverBand: '',
        numberOfEmployees: '12',
        taxId: '',
      },
      individual,
    });
    expect(parsed.individual).toBeUndefined();
    expect(parsed.business?.numberOfEmployees).toBe(12);
  });
});
