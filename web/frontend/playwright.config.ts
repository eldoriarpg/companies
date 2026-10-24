/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {defineConfig, devices} from '@playwright/test'

/** Where the mock gateway listens. */
const MOCK_PORT = 8787

/** Where the built frontend listens. */
const APP_PORT = 4000

/**
 * The end-to-end suite. It walks the built application in real browsers: Chromium and Firefox at desktop size, and
 * the phone profiles of a Pixel in Chromium and an iPhone in WebKit, since the dashboard is used on phones first.
 *
 * <p>Unless `COMPANIES_E2E_URL` names a running dashboard, it starts two servers: the mock gateway of
 * `e2e/mock/backend.mjs`, and the production build in `.output` (`npm run build` first) with its proxy and stream
 * relay pointed at the mock. So the stories cross the same Nitro server a deployment runs.
 *
 * <p>Specs are named after their feature as `<feature>.e2e.ts`, which Playwright's default pattern does not find, so
 * `testMatch` names the pattern.
 */
export default defineConfig({
    testDir: './e2e',
    outputDir: './e2e/results',
    testMatch: /.*\.e2e\.ts/,
    timeout: 60_000,
    expect: {timeout: 15_000},
    retries: process.env.CI ? 1 : 0,
    fullyParallel: true,
    reporter: process.env.CI ? [['html', {outputFolder: 'e2e/report', open: 'never'}], ['list']] : 'list',

    use: {
        baseURL: process.env.COMPANIES_E2E_URL || `http://localhost:${APP_PORT}`,
        actionTimeout: 30_000,
        trace: 'on-first-retry',
        screenshot: 'only-on-failure',
    },

    projects: [
        {name: 'chromium', use: {...devices['Desktop Chrome']}},
        {name: 'firefox', use: {...devices['Desktop Firefox']}},
        {name: 'pixel', use: {...devices['Pixel 7']}},
        {name: 'iphone', use: {...devices['iPhone 14']}},
    ],

    webServer: process.env.COMPANIES_E2E_URL ? undefined : [
        {
            command: 'node e2e/mock/backend.mjs',
            url: `http://localhost:${MOCK_PORT}/api/v1/s/MOSS01`,
            env: {MOCK_PORT: String(MOCK_PORT), MOCK_PUBLIC_URL: `http://localhost:${APP_PORT}`},
            reuseExistingServer: !process.env.CI,
            timeout: 30_000,
        },
        {
            command: 'node .output/server/index.mjs',
            url: `http://localhost:${APP_PORT}`,
            env: {PORT: String(APP_PORT), NUXT_BACKEND_URL: `http://localhost:${MOCK_PORT}`},
            reuseExistingServer: !process.env.CI,
            timeout: 60_000,
        },
    ],
})
