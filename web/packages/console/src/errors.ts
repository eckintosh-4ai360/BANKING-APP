import { ApiError } from '@banking/api';
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';

/** A message fit for the user. Server messages are shown as-is: the backend writes them for end users. */
export function errorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (error instanceof ApiError) {
    if (error.status >= 500) {
      return error.traceId ? `${fallback} (reference ${error.traceId})` : fallback;
    }
    return error.message;
  }
  return fallback;
}

/**
 * Copies server-side field validation errors onto the form, so the backend's rules (the authoritative ones) show next
 * to the right inputs. Returns true when at least one field error was applied.
 */
export function applyFieldErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>, fields: readonly Path<T>[]): boolean {
  if (!(error instanceof ApiError) || error.fieldErrors.length === 0) {
    return false;
  }
  let applied = false;
  for (const violation of error.fieldErrors) {
    const field = fields.find((name) => name === violation.field);
    if (field) {
      setError(field, { type: 'server', message: violation.message });
      applied = true;
    }
  }
  return applied;
}

/** Only same-app relative paths may be used as post-login destinations (no open redirects). */
export function safeNextPath(raw: string | null | undefined, fallback = '/'): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\') || raw.includes('://')) {
    return fallback;
  }
  if (raw.startsWith('/login') || raw.startsWith('/api/')) {
    return fallback;
  }
  return raw;
}

/** Client-side mirror of the backend password rules, for early feedback only (the backend enforces them). */
export function passwordProblems(password: string): string | undefined {
  const length = Array.from(password).length;
  if (length < 12) {
    return 'Use at least 12 characters';
  }
  if (length > 128) {
    return 'Use at most 128 characters';
  }
  if (new Set(Array.from(password)).size < 6) {
    return 'Use at least 6 different characters';
  }
  return undefined;
}
