import type { NextRequest } from 'next/server';
import { getBff } from '@/lib/bff';

export function GET(request: NextRequest): Promise<Response> {
  return getBff().auth.session(request);
}
