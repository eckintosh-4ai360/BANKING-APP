import type { UserConfig } from 'vite';

/**
 * Shared Vitest settings. JSX is compiled by Vite's built-in Oxc transform with the automatic runtime, so no React
 * plugin is needed for tests.
 */
export function vitestConfig(environment: 'node' | 'jsdom', setupFiles: string[] = []): UserConfig & { test: Record<string, unknown> } {
  return {
    oxc: { jsx: { runtime: 'automatic' } },
    test: {
      environment,
      setupFiles,
      include: ['src/**/*.test.{ts,tsx}', 'test/**/*.test.{ts,tsx}'],
      restoreMocks: true,
      unstubGlobals: true,
      unstubEnvs: true,
    },
  };
}
