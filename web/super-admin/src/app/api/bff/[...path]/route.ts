import type { NextRequest } from 'next/server';
import { getBff } from '@/lib/bff';

/** Every browser call to banking-core goes through here; see @banking/bff for the checks applied. */
async function handle(request: NextRequest, context: { params: Promise<{ path: string[] }> }): Promise<Response> {
  const { path } = await context.params;
  return getBff().proxy(request, path);
}

export { handle as GET, handle as POST, handle as PUT, handle as PATCH, handle as DELETE };
