import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';
import { vitestConfig } from '../vitest.shared.ts';

const base = vitestConfig('jsdom', ['../vitest.setup.dom.ts']);

export default defineConfig({
  ...base,
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
});
