/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test} from '@playwright/test'
import {openDashboard} from './session'

/** Servers on a plugin version the dashboard is about to drop, or has dropped. */
test.describe('plugin versions', () => {
    test('warns on every page of a deprecated server', async ({page}) => {
        await openDashboard(page, 'DEPR01')
        const banner = page.getByRole('status').filter({hasText: 'supports until'})
        await expect(banner).toBeVisible()
        await expect(banner).toContainText('Companies 2.1 uses an old order format the dashboard stops reading.')
        await expect(banner).toContainText('Companies 2.4.0 or newer')
    })

    test('shows only the explanation for a discontinued server', async ({page}) => {
        await page.goto('/GONE01')
        const notice = page.getByRole('alert')
        await expect(notice).toContainText('The dashboard no longer supports Mossvale SMP.')
        await expect(notice).toContainText('Companies 2.0 uses an order format the dashboard no longer reads.')
        await expect(page.getByLabel('Login code')).toHaveCount(0)
    })
})
