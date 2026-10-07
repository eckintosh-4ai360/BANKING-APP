import { defineConfig } from 'vitest/config';
import { vitestConfig } from '../../vitest.shared.ts';

export default defineConfig(vitestConfig('jsdom', ['../../vitest.setup.dom.ts']));
