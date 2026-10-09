import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, bff, hasAny, newIdempotencyKey, query, setUnauthenticatedHandler } from '../src';

function respond(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
}

afterEach(() => setUnauthenticatedHandler(undefined));

describe('bff client', () => {
  it('calls the BFF with the CSRF header and unwraps the envelope', async () => {
    const fetchMock = vi.fn(async () => respond(200, { success: true, message: 'OK', data: { id: 'b1' }, timestamp: 'now' }));
    vi.stubGlobal('fetch', fetchMock);

    const data = await bff<{ id: string }>('/branches', { method: 'POST', body: { code: 'TEMA' } });

    expect(data).toEqual({ id: 'b1' });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/bff/branches');
    expect(init.credentials).toBe('same-origin');
    const headers = init.headers as Record<string, string>;
    expect(headers['x-requested-with']).toBe('banking-bff');
    expect(headers['content-type']).toBe('application/json');
    expect(init.body).toBe('{"code":"TEMA"}');
  });

  it('sends the idempotency key of a money movement and makes a new key per operation', async () => {
    const fetchMock = vi.fn(async () => respond(201, { success: true, data: { outcome: 'POSTED' } }));
    vi.stubGlobal('fetch', fetchMock);
    const key = newIdempotencyKey();

    await bff('/transactions/deposits', { body: { accountId: 'a1', amount: '10.00' }, idempotencyKey: key });

    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect((init.headers as Record<string, string>)['idempotency-key']).toBe(key);
    expect(key).toMatch(/^[A-Za-z0-9_-]{8,100}$/);
    expect(newIdempotencyKey()).not.toBe(key);
  });

  it('turns error envelopes into ApiError with field errors and trace id', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        respond(400, {
          success: false,
          code: 'VALIDATION_FAILED',
          message: 'Invalid request',
          traceId: 't-1',
          errors: [{ field: 'code', message: 'must not be blank' }],
        }),
      ),
    );
    const error = await bff('/branches', { method: 'POST', body: {} }).catch((caught: unknown) => caught);
    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.code).toBe('VALIDATION_FAILED');
    expect(apiError.traceId).toBe('t-1');
    expect(apiError.fieldError('code')).toBe('must not be blank');
  });

  it('notifies the app when the session has ended', async () => {
    const handler = vi.fn();
    setUnauthenticatedHandler(handler);
    vi.stubGlobal('fetch', vi.fn(async () => respond(401, { success: false, code: 'UNAUTHENTICATED', message: 'Signed out' })));
    await expect(bff('/me')).rejects.toBeInstanceOf(ApiError);
    expect(handler).toHaveBeenCalledOnce();
  });

  it('sends FormData without forcing a JSON content type', async () => {
    const fetchMock = vi.fn(async () => respond(201, { success: true, data: { id: 'd1' } }));
    vi.stubGlobal('fetch', fetchMock);
    const form = new FormData();
    form.set('documentType', 'NATIONAL_ID');
    await bff('/customers/c1/documents', { method: 'POST', body: form });
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect((init.headers as Record<string, string>)['content-type']).toBeUndefined();
    expect(init.body).toBe(form);
  });

  it('handles empty and non-JSON error bodies', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('<html>Bad gateway</html>', { status: 502 })));
    const error = (await bff('/me').catch((caught: unknown) => caught)) as ApiError;
    expect(error.code).toBe('INTERNAL_ERROR');
  });
});

describe('helpers', () => {
  it('builds query strings without empty values', () => {
    expect(query({ q: 'ama mensah', page: 0, status: undefined, branchId: '' })).toBe('?q=ama+mensah&page=0');
    expect(query({})).toBe('');
  });

  it('checks permissions', () => {
    expect(hasAny(['customer.view'], ['customer.view', 'customer.edit'])).toBe(true);
    expect(hasAny(['customer.view'], ['kyc.approve'])).toBe(false);
    expect(hasAny(undefined, ['kyc.approve'])).toBe(false);
    expect(hasAny([], [])).toBe(true);
  });
});
