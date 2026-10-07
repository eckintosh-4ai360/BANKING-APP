import { describe, expect, it } from 'vitest';
import { visibleNavigation } from '@/lib/navigation';
import { GHANA_DEFAULTS, onboardingSchema } from '@/lib/onboarding';

const valid = {
  code: 'Akwaaba-MFI',
  legalName: 'Akwaaba Microfinance Ltd',
  displayName: 'Akwaaba MFI',
  institutionType: 'MICROFINANCE' as const,
  ...GHANA_DEFAULTS,
  licenceNumber: '',
  contactEmail: 'info@akwaaba.example',
  contactPhone: '',
  headOffice: { code: 'hq', name: 'Head Office', city: 'Accra', region: '', digitalAddress: '' },
  administrator: { firstName: 'Kwame', lastName: 'Asante', email: 'kwame@akwaaba.example', phone: '', username: 'Kwame.Admin' },
  features: ['SAVINGS', 'LOANS'],
};

describe('onboardingSchema', () => {
  it('normalises codes and blanks before sending', () => {
    const parsed = onboardingSchema.parse(valid);
    expect(parsed.code).toBe('akwaaba-mfi');
    expect(parsed.headOffice.code).toBe('HQ');
    expect(parsed.administrator.username).toBe('kwame.admin');
    expect(parsed.licenceNumber).toBeUndefined();
    expect(parsed.headOffice.region).toBeUndefined();
  });

  it('rejects invalid institution data', () => {
    const result = onboardingSchema.safeParse({
      ...valid,
      code: 'x',
      baseCurrency: 'CEDI',
      contactEmail: 'not-an-email',
      administrator: { ...valid.administrator, username: '!!' },
    });
    const paths = result.error?.issues.map((issue) => issue.path.join('.'));
    expect(paths).toEqual(expect.arrayContaining(['code', 'baseCurrency', 'contactEmail', 'administrator.username']));
  });
});

describe('platform navigation', () => {
  it('hides institutions and audit without the platform permissions', () => {
    const labels = visibleNavigation([], '/').flatMap((group) => group.items.map((item) => item.label));
    expect(labels).toEqual(['Account security']);
  });

  it('treats tenant pages as part of Institutions', () => {
    const items = visibleNavigation(['platform.tenant.view', 'platform.audit.view'], '/tenants/123').flatMap((group) => group.items);
    expect(items.find((item) => item.label === 'Institutions')?.active).toBe(true);
    expect(items.find((item) => item.label === 'Platform audit')?.active).toBe(false);
  });
});
