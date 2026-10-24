/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test} from '@playwright/test'
import {openDashboard} from './session'

test.beforeEach(async ({page}) => openDashboard(page, 'CALM01'))

/** Moving items from the web while the player is online: delivering, receiving and collecting. */
test.describe('item actions', () => {
    test('delivers what the player carries to a position', async ({page}) => {
        const position = page.getByTestId('positions').getByRole('listitem').filter({hasText: 'Harbour lighthouse'})

        await position.getByRole('button', {name: 'Deliver what I carry'}).click()

        await expect(page.getByText('Delivered 18 items to Harbour lighthouse.')).toBeVisible()
    })

    test('says when the player carries nothing a line needs', async ({page}) => {
        const position = page.getByTestId('positions').getByRole('listitem').filter({hasText: 'Harbour lighthouse'})

        await position.getByRole('button', {name: /Deliver Prismarine/}).click()

        await expect(page.getByText('You carry nothing Harbour lighthouse still needs.')).toBeVisible()
    })

    test('receives a delivered order and collects what waits', async ({page}) => {
        await page.getByRole('button', {name: 'Receive into inventory'}).click()
        await expect(page.getByText('Garden lights is received; 20 items did not fit and wait to be collected.')).toBeVisible()

        await page.getByRole('button', {name: 'Collect waiting items'}).click()
        await expect(page.getByText('Collected 12 items.')).toBeVisible()
    })
})
