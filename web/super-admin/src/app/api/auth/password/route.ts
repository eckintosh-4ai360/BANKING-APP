import type { NextRequest } from 'next/server';
import { getBff } from '@/lib/bff';

export function POST(request: NextRequest): Promise<Response> {
  return getBff().auth.changePassword(request);
}
