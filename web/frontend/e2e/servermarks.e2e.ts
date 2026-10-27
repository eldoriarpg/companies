/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test} from '@playwright/test'
import {onPhone, openDashboard, openSection} from './session'

/** Orders the server placed itself: marked and filterable only while the server marks them. */
test.describe('server orders', () => {
    test('carry a badge and can be shown alone or hidden', async ({page}, info) => {
        await openDashboard(page, 'SRVR01')
        await openSection(page, info, 'Order book')
        const book = page.getByTestId('order-book')
        await expect(book.getByTestId('server-badge')).toHaveCount(1)

        await page.getByRole('button', {name: 'Server orders'}).click()
        await expect(book.getByRole('listitem')).toHaveCount(1)
        await expect(book.getByText('Aquarium')).toBeVisible()
        await book.getByText('Aquarium').click()
        const details = onPhone(info) ? page.getByRole('dialog') : page.getByRole('complementary', {name: 'Order details'})
        await expect(details.getByText('Placed by the server')).toBeVisible()
    })

    test('look like any order while the server does not mark them', async ({page}, info) => {
        await openDashboard(page, 'CALM01')
        await openSection(page, info, 'Order book')
        await expect(page.getByTestId('order-book').getByText('Aquarium')).toBeVisible()
        await expect(page.getByTestId('server-badge')).toHaveCount(0)
        await expect(page.getByRole('button', {name: 'Server orders'})).toHaveCount(0)
    })
})
