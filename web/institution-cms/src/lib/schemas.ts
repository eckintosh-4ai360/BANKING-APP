import { z } from 'zod';

/**
 * Client-side mirrors of banking-core's request validation. They exist for fast feedback only; the backend validates
 * every request again and its errors are shown next to the same fields.
 */

export const PHONE = /^\+?[0-9]{7,15}$/;
export const COUNTRY = /^[A-Z]{2}$/;
export const DIGITAL_ADDRESS = /^[A-Za-z0-9-]{4,20}$/;
export const BRANCH_CODE = /^[A-Z0-9][A-Z0-9-]{0,19}$/;
export const ROLE_CODE = /^[A-Z][A-Z0-9_]{1,49}$/;
export const USERNAME = /^[A-Za-z0-9][A-Za-z0-9._@-]{2,99}$/;

/** Optional text: trimmed, and empty input becomes undefined so the backend receives null rather than "". */
export function optionalText(max: number) {
  return z
    .string()
    .trim()
    .max(max, `Use at most ${max} characters`)
    .transform((value) => (value === '' ? undefined : value));
}

export function requiredText(max: number, message = 'Required') {
  return z.string().trim().min(1, message).max(max, `Use at most ${max} characters`);
}

export function optionalPattern(pattern: RegExp, message: string) {
  return z
    .string()
    .trim()
    .refine((value) => value === '' || pattern.test(value), message)
    .transform((value) => (value === '' ? undefined : value));
}

export const optionalPhone = optionalPattern(PHONE, 'Use 7 to 15 digits, optionally starting with +');
export const optionalEmail = z
  .string()
  .trim()
  .max(254)
  .refine((value) => value === '' || z.email().safeParse(value).success, 'Enter a valid email address')
  .transform((value) => (value === '' ? undefined : value));

/** A yyyy-MM-dd date from an <input type="date">, optional. */
export const optionalDate = z
  .string()
  .refine((value) => value === '' || /^\d{4}-\d{2}-\d{2}$/.test(value), 'Enter a valid date')
  .transform((value) => (value === '' ? undefined : value));

export function isPastDate(value: string, today = new Date()): boolean {
  return value < today.toISOString().slice(0, 10);
}

const individual = z.object({
  title: optionalText(20),
  firstName: requiredText(100, 'Enter the first name'),
  middleName: optionalText(100),
  lastName: requiredText(100, 'Enter the last name'),
  dateOfBirth: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Enter the date of birth')
    .refine((value) => isPastDate(value), 'The date of birth must be in the past'),
  gender: z.enum(['', 'MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED']).transform((value) => value || undefined),
  nationality: optionalPattern(COUNTRY, 'Use a 2-letter country code, e.g. GH'),
  maritalStatus: z.enum(['', 'SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'SEPARATED', 'UNDISCLOSED']).transform((value) => value || undefined),
  occupation: optionalText(100),
  employerName: optionalText(150),
  employmentStatus: z.enum(['', 'EMPLOYED', 'SELF_EMPLOYED', 'UNEMPLOYED', 'STUDENT', 'RETIRED', 'OTHER']).transform((value) => value || undefined),
  monthlyIncomeBand: optionalText(30),
  taxId: z
    .string()
    .trim()
    .refine((value) => value === '' || (value.length >= 5 && value.length <= 30), 'Use 5 to 30 characters')
    .transform((value) => (value === '' ? undefined : value)),
});

const business = z.object({
  registeredName: requiredText(200, 'Enter the registered name'),
  tradingName: optionalText(200),
  registrationNumber: requiredText(50, 'Enter the registration number'),
  registrationDate: optionalDate,
  businessType: z.enum(['SOLE_PROPRIETORSHIP', 'PARTNERSHIP', 'LIMITED_COMPANY', 'COOPERATIVE', 'NGO', 'ASSOCIATION', 'OTHER']),
  industrySector: optionalText(100),
  annualTurnoverBand: optionalText(30),
  numberOfEmployees: z
    .string()
    .trim()
    .refine((value) => value === '' || /^\d{1,7}$/.test(value), 'Enter a whole number')
    .transform((value) => (value === '' ? undefined : Number.parseInt(value, 10))),
  taxId: z
    .string()
    .trim()
    .refine((value) => value === '' || (value.length >= 5 && value.length <= 30), 'Use 5 to 30 characters')
    .transform((value) => (value === '' ? undefined : value)),
});

const contact = {
  primaryPhone: optionalPhone,
  email: optionalEmail,
  preferredLanguage: optionalText(10),
};

/**
 * One form for both customer types: only the section matching the type is mounted (the form unregisters the other),
 * and the refinement makes that section mandatory.
 */
export const createCustomerSchema = z
  .object({
    customerType: z.enum(['INDIVIDUAL', 'BUSINESS']),
    homeBranchId: z.string().uuid('Choose the home branch'),
    onboardingChannel: z.enum(['BRANCH', 'FIELD']),
    ...contact,
    individual: individual.optional(),
    business: business.optional(),
  })
  .superRefine((values, context) => {
    if (values.customerType === 'INDIVIDUAL' && !values.individual) {
      context.addIssue({ code: 'custom', path: ['individual'], message: 'Enter the personal details' });
    }
    if (values.customerType === 'BUSINESS' && !values.business) {
      context.addIssue({ code: 'custom', path: ['business'], message: 'Enter the business details' });
    }
  })
  .transform((values) =>
    values.customerType === 'INDIVIDUAL' ? { ...values, business: undefined } : { ...values, individual: undefined },
  );

export type CreateCustomerInput = z.input<typeof createCustomerSchema>;
export type CreateCustomerValues = z.output<typeof createCustomerSchema>;

export const contactSchema = z.object({ ...contact });
