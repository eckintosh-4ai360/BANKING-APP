/** JSON responses in the backend's envelope format, so the browser client handles BFF and backend errors alike. */

export function jsonOk<T>(data: T, init: { status?: number; headers?: HeadersInit; setCookies?: string[] } = {}): Response {
  return jsonResponse(init.status ?? 200, { success: true, message: 'OK', data, timestamp: new Date().toISOString() }, init);
}

export function jsonError(status: number, code: string, message: string, init: { headers?: HeadersInit; setCookies?: string[] } = {}): Response {
  return jsonResponse(status, { success: false, code, message, timestamp: new Date().toISOString() }, init);
}

function jsonResponse(status: number, body: unknown, init: { headers?: HeadersInit; setCookies?: string[] }): Response {
  const headers = new Headers(init.headers);
  headers.set('content-type', 'application/json');
  headers.set('cache-control', 'no-store');
  for (const cookie of init.setCookies ?? []) {
    headers.append('set-cookie', cookie);
  }
  return new Response(JSON.stringify(body), { status, headers });
}

export function appendCookies(response: Response, cookies: string[]): Response {
  for (const cookie of cookies) {
    response.headers.append('set-cookie', cookie);
  }
  return response;
}
