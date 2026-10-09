'use client';

import { ApiError, BFF_HEADER, BFF_HEADER_VALUE } from '@banking/api';

/** File types the console may save; anything else is saved as opaque bytes so it can never render in our origin. */
const SAVEABLE = ['application/pdf', 'text/csv'];

/**
 * Downloads a file through the BFF (session cookie + CSRF header) and hands it to the browser as a download. The file
 * name comes from the server's Content-Disposition when present.
 */
export async function downloadThroughBff(path: string, fallbackName: string): Promise<void> {
  const response = await fetch(`/api/bff${path}`, {
    headers: { [BFF_HEADER]: BFF_HEADER_VALUE },
    credentials: 'same-origin',
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as { code?: string; message?: string } | null;
    throw new ApiError(response.status, body);
  }
  const declared = (response.headers.get('content-type') ?? '').split(';')[0]?.trim() ?? '';
  const type = SAVEABLE.includes(declared) ? declared : 'application/octet-stream';
  const blob = new Blob([await response.arrayBuffer()], { type });
  const url = URL.createObjectURL(blob);
  try {
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileNameOf(response.headers.get('content-disposition')) ?? fallbackName;
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
  } finally {
    setTimeout(() => URL.revokeObjectURL(url), 10_000);
  }
}

/** The file name of an attachment, limited to safe characters. */
export function fileNameOf(disposition: string | null): string | undefined {
  const match = disposition ? /filename="?([^";]+)"?/i.exec(disposition) : null;
  const name = match?.[1]?.replace(/[^A-Za-z0-9._-]/g, '_');
  return name && name.length > 0 ? name : undefined;
}
