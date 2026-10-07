import { z } from 'zod';

/** Mirrors OnboardTenantRequest validation in banking-core (which validates again). */

const blankToUndefined = (value: string) => (value === '' ? undefined : value);
const optional = (max: number) => z.string().trim().max(max).transform(blankToUndefined);
const optionalMatching = (pattern: RegExp, message: string) =>
  z
    .string()
    .trim()
    .refine((value) => value === '' || pattern.test(value), message)
    .transform(blankToUndefined);

const PHONE = /^\+?[0-9]{7,15}$/;
const email = z.string().trim().max(254).pipe(z.email('Enter a valid email address'));

export const INSTITUTION_TYPES = [
  'MICROFINANCE',
  'SAVINGS_AND_LOANS',
  'CREDIT_UNION',
  'RURAL_COMMUNITY_BANK',
  'SUSU_OPERATOR',
  'DIGITAL_LENDER',
  'COOPERATIVE',
  'OTHER',
] as const;

export const onboardingSchema = z.object({
  code: z
    .string()
    .trim()
    .toLowerCase()
    .regex(/^[a-z0-9][a-z0-9-]{1,31}$/, 'Use 2-32 lower-case letters, digits or hyphens'),
  legalName: z.string().trim().min(1, 'Enter the legal name').max(200),
  displayName: z.string().trim().min(1, 'Enter the display name').max(120),
  institutionType: z.enum(INSTITUTION_TYPES),
  countryCode: z.string().trim().toUpperCase().regex(/^[A-Z]{2}$/, 'Use a 2-letter country code'),
  baseCurrency: z.string().trim().toUpperCase().regex(/^[A-Z]{3}$/, 'Use a 3-letter currency code'),
  timezone: z.string().trim().min(1).max(64),
  locale: z.string().trim().min(1).max(20),
  licenceNumber: optional(64),
  contactEmail: email,
  contactPhone: optionalMatching(PHONE, 'Use 7 to 15 digits, optionally starting with +'),
  headOffice: z.object({
    code: z
      .string()
      .trim()
      .toUpperCase()
      .regex(/^[A-Z0-9][A-Z0-9-]{0,19}$/, 'Use 1-20 letters, digits or hyphens'),
    name: z.string().trim().min(1, 'Enter the head office name').max(120),
    city: optional(100),
    region: optional(100),
    digitalAddress: optionalMatching(/^[A-Za-z0-9-]{4,20}$/, 'e.g. GA-123-4567'),
  }),
  administrator: z.object({
    firstName: z.string().trim().min(1, 'Enter the first name').max(100),
    lastName: z.string().trim().min(1, 'Enter the last name').max(100),
    email,
    phone: optionalMatching(PHONE, 'Use 7 to 15 digits, optionally starting with +'),
    username: z
      .string()
      .trim()
      .toLowerCase()
      .regex(/^[A-Za-z0-9][A-Za-z0-9._@-]{2,99}$/, 'Use 3-100 letters, digits or . _ @ -'),
  }),
  features: z.array(z.string()).max(50),
});

export type OnboardingInput = z.input<typeof onboardingSchema>;
export type OnboardingValues = z.output<typeof onboardingSchema>;

export const GHANA_DEFAULTS: Pick<OnboardingInput, 'countryCode' | 'baseCurrency' | 'timezone' | 'locale'> = {
  countryCode: 'GH',
  baseCurrency: 'GHS',
  timezone: 'Africa/Accra',
  locale: 'en-GH',
};
