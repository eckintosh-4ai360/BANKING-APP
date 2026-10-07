/**
 * Content-Security-Policy for the console pages.
 *  - scripts: only Next.js' own, identified by the per-request nonce ('strict-dynamic' lets them load chunks);
 *  - connections: same origin only, so the browser can talk to nothing but the BFF;
 *  - styles allow inline because development-mode style injection carries no nonce (styles can't run script);
 *  - 'unsafe-eval' only in development, where React uses it for error overlays.
 */
export function contentSecurityPolicy(nonce: string, development: boolean): string {
  const directives = [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'${development ? " 'unsafe-eval'" : ''}`,
    "style-src 'self' 'unsafe-inline'",
    "img-src 'self' blob: data:",
    "font-src 'self'",
    "connect-src 'self'",
    "frame-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    ...(development ? [] : ['upgrade-insecure-requests']),
  ];
  return directives.join('; ');
}
