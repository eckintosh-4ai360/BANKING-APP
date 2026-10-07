import { ApiError } from '@banking/api';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginForm } from '../src/auth/login-form';
import { PasswordChangeForm } from '../src/auth/security-forms';
import { OneTimeCredentialDialog } from '../src/components/one-time-secret';
import { passwordProblems, safeNextPath } from '../src/errors';
import { landingPath } from '../src/hooks';
import { DEFAULT_ROUTES, redirectFor } from '../src/providers';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

beforeEach(() => {
  window.localStorage.clear();
});

describe('safeNextPath', () => {
  it.each([
    ['/customers?q=ama', '/customers?q=ama'],
    ['//evil.test/path', '/'],
    ['/\\evil.test', '/'],
    ['https://evil.test', '/'],
    ['/redirect?to=https://evil.test', '/'],
    ['/login?next=/x', '/'],
    ['/api/bff/me', '/'],
    [null, '/'],
    ['customers', '/'],
  ])('%s → %s', (input, expected) => {
    expect(safeNextPath(input)).toBe(expected);
  });
});

describe('redirectFor', () => {
  it('sends ended sessions to the login page, remembering where the user was', () => {
    expect(redirectFor(new ApiError(401, { code: 'UNAUTHENTICATED' }), DEFAULT_ROUTES, '/customers?q=ama')).toBe(
      '/login?next=%2Fcustomers%3Fq%3Dama',
    );
    expect(redirectFor(new ApiError(401, null), DEFAULT_ROUTES, '/login')).toBeNull();
  });

  it('sends restricted sessions to the matching setup page', () => {
    expect(redirectFor(new ApiError(403, { code: 'PASSWORD_CHANGE_REQUIRED' }), DEFAULT_ROUTES, '/')).toBe('/setup/password');
    expect(redirectFor(new ApiError(403, { code: 'MFA_ENROLLMENT_REQUIRED' }), DEFAULT_ROUTES, '/')).toBe('/setup/mfa');
    expect(redirectFor(new ApiError(403, { code: 'ACCESS_DENIED' }), DEFAULT_ROUTES, '/')).toBeNull();
    expect(redirectFor(new Error('x'), DEFAULT_ROUTES, '/')).toBeNull();
  });

  it('routes a new session by its restrictions', () => {
    expect(landingPath({ passwordChangeRequired: true, mfaEnrollmentRequired: true }, '/kyc')).toBe('/setup/password');
    expect(landingPath({ passwordChangeRequired: false, mfaEnrollmentRequired: true }, '/kyc')).toBe('/setup/mfa');
    expect(landingPath({ passwordChangeRequired: false, mfaEnrollmentRequired: false }, '/kyc')).toBe('/kyc');
  });
});

describe('passwordProblems', () => {
  it('mirrors the backend minimums', () => {
    expect(passwordProblems('short')).toMatch(/12 characters/);
    expect(passwordProblems('aaaaaaaaaaaaaaab')).toMatch(/6 different/);
    expect(passwordProblems('correct horse battery')).toBeUndefined();
  });
});

describe('LoginForm', () => {
  it('signs in a staff user through the MFA step', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(200, { success: true, data: { mfaRequired: true, passwordChangeRequired: false, mfaEnrollmentRequired: false } }))
      .mockResolvedValueOnce(json(200, { success: true, data: { mfaRequired: false, passwordChangeRequired: false, mfaEnrollmentRequired: false } }));
    vi.stubGlobal('fetch', fetchMock);
    const onSignedIn = vi.fn();
    const user = userEvent.setup();
    render(<LoginForm kind="staff" onSignedIn={onSignedIn} />);

    await user.type(screen.getByLabelText(/Institution code/), 'Demo-MFI');
    await user.type(screen.getByLabelText(/Username/), 'admin');
    await user.type(screen.getByLabelText(/^Password/), 'Demo@Pass2026');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    const codeInput = await screen.findByLabelText(/Authentication code/);
    expect(JSON.parse((fetchMock.mock.calls[0] as [string, RequestInit])[1].body as string)).toEqual({
      tenantCode: 'demo-mfi',
      username: 'admin',
      password: 'Demo@Pass2026',
    });
    await user.type(codeInput, '123456');
    await user.click(screen.getByRole('button', { name: 'Verify' }));

    await waitFor(() => expect(onSignedIn).toHaveBeenCalledWith({ mfaRequired: false, passwordChangeRequired: false, mfaEnrollmentRequired: false }));
    expect((fetchMock.mock.calls[1] as [string])[0]).toBe('/api/auth/mfa');
  });

  it('shows the backend message and clears the password on failure', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(401, { success: false, code: 'INVALID_CREDENTIALS', message: 'Invalid username or password.' })));
    const user = userEvent.setup();
    render(<LoginForm kind="platform" onSignedIn={vi.fn()} />);

    expect(screen.queryByLabelText(/Institution code/)).toBeNull();
    await user.type(screen.getByLabelText(/Username/), 'platform.owner');
    await user.type(screen.getByLabelText(/^Password/), 'wrong-password');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Invalid username or password.')).toBeInTheDocument();
    expect(screen.getByLabelText(/^Password/)).toHaveValue('');
  });

  it('validates before calling the server', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<LoginForm kind="staff" onSignedIn={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Enter your institution code')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('PasswordChangeForm', () => {
  it('requires matching passwords and shows server-side policy errors next to the field', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        json(400, {
          success: false,
          code: 'VALIDATION_FAILED',
          message: 'Invalid request',
          errors: [{ field: 'newPassword', message: 'The password is too common.' }],
        }),
      ),
    );
    const user = userEvent.setup();
    render(<PasswordChangeForm onChanged={vi.fn()} />);
    await user.type(screen.getByLabelText(/Current password/), 'Temp-Password-1');
    await user.type(screen.getByLabelText(/^New password/), 'correct horse battery');
    await user.type(screen.getByLabelText(/Confirm new password/), 'correct horse batteryX');
    await user.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText('The passwords do not match')).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Confirm new password/), '{Backspace}');
    await user.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText('The password is too common.')).toBeInTheDocument();
  });
});

describe('OneTimeCredentialDialog', () => {
  it('cannot be dismissed until the user confirms the password was handed over', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<OneTimeCredentialDialog title="Staff created" credential={{ username: 'ama', temporaryPassword: 'Xy7#kP2m!Qw9Lz4R' }} onClose={onClose} />);

    expect(screen.getByText('Xy7#kP2m!Qw9Lz4R')).toBeInTheDocument();
    const done = screen.getByRole('button', { name: 'Done' });
    expect(done).toBeDisabled();
    await user.keyboard('{Escape}');
    expect(onClose).not.toHaveBeenCalled();

    await user.click(screen.getByLabelText(/passed these details on/));
    await user.click(done);
    expect(onClose).toHaveBeenCalledOnce();
  });
});
