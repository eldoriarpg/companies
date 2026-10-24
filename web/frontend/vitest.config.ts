/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {defineVitestConfig} from '@nuxt/test-utils/config'

/**
 * Nuxt owns the Vite config, so the test runner takes its environment from `@nuxt/test-utils`
 * rather than from a hand-written Vite config. That is what makes auto-imports, `#imports` and the
 * `~`/`@` aliases resolve in a test exactly as they do in the app.
 *
 * <p>The Nuxt environment costs startup time. A test touching no Nuxt API opts out per file with a
 * `@vitest-environment happy-dom` docblock and runs an order of magnitude faster.
 *
 * <p>Booting Nuxt is the `beforeAll` of every file that has not opted out, and on a loaded machine
 * it does not fit in the ten seconds a hook is given by default, hence the longer hook timeout. The
 * project's own ESLint rules under `eslint/` are tested here as well as the application, and so is
 * the WebSocket relay of the server. The end-to-end mock backend is plain JavaScript and has none.
 */
export default defineVitestConfig({
    test: {
        environment: 'nuxt',
        environmentOptions: {
            nuxt: {domEnvironment: 'happy-dom'},
        },
        hookTimeout: 30_000,
        globals: true,
        include: ['src/**/*.{test,spec}.ts', 'server/**/*.{test,spec}.ts', 'scripts/**/*.{test,spec}.ts', 'eslint/**/*.{test,spec}.ts'],
        exclude: ['e2e/**', 'node_modules/**', '.nuxt/**'],
        setupFiles: ['src/test/setup.ts'],
        coverage: {
            provider: 'v8',
            reporter: ['text', 'lcov', 'html'],
            include: ['src/**/*.{ts,vue}'],
            exclude: [
                'src/**/*.{test,spec}.ts',
                'src/test/**',
                'src/i18n/**',
                'src/pages/**',
            ],
        },
    },
})
