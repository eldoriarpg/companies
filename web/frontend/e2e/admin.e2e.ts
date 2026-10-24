/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test, type Page, type TestInfo} from '@playwright/test'
import {onPhone, openDashboard} from './session'

/**
 * Opens the admin section: from the sidebar on a desktop, from the menu on a phone.
 *
 * @param page the page
 * @param info the test, for the device
 */
async function openAdmin(page: Page, info: TestInfo): Promise<void> {
    if (onPhone(info)) {
        await page.getByRole('button', {name: 'Menu'}).click()
        await page.getByRole('dialog').getByRole('link', {name: 'Admin'}).click()
    } else {
        await page.getByRole('navigation', {name: 'Sections'}).filter({visible: true}).getByRole('link', {name: 'Admin'}).click()
    }
}

/** The admin tools: shown to admins only, opening and finding any order, and changing it after asking. */
test.describe('admin', () => {
    test('is not offered to players without admin rights', async ({page}) => {
        await openDashboard(page, 'CALM01')
        await expect(page.getByRole('link', {name: 'Admin'})).toHaveCount(0)
    })

    test('opens an order by its id and deletes it with a refund', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)

        await page.getByLabel('Order id').fill('301')
        await page.getByRole('button', {name: 'Open', exact: true}).click()
        const order = page.getByTestId('admin-order')
        await expect(order.getByText('Orla')).toBeVisible()
        await expect(order.getByText('Kestrel_ 120, mossycobble 80')).toBeVisible()

        await order.getByRole('button', {name: 'Delete with refund'}).click()
        await expect(order.getByText('Delete Harbour wall and give the owner 1,155.00 back?')).toBeVisible()
        await order.getByRole('button', {name: 'Delete', exact: true}).click()

        await expect(page.getByText('Harbour wall is deleted; the owner got 1,155.00 back.')).toBeVisible()
    })

    test('finds the orders of a player and gives one more time', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)

        await page.getByLabel('Player name').fill('orla')
        await page.getByRole('button', {name: 'Find', exact: true}).click()
        await page.getByTestId('admin-orders').getByRole('button', {name: '#301 Harbour wall'}).click()
        const order = page.getByTestId('admin-order')
        await order.getByLabel('Hours to add').fill('24')
        await order.getByRole('button', {name: 'Give more time'}).click()

        await expect(order.getByText('The order has 24 more hours.')).toBeVisible()
    })
})
