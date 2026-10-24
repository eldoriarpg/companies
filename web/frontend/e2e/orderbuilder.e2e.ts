/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test, type Page, type TestInfo} from '@playwright/test'
import {openDashboard, openSection} from './session'

test.beforeEach(async ({page}) => openDashboard(page, 'CALM01'))

/**
 * Opens the order builder from the order book.
 *
 * @param page the page
 * @param info the test, for the device
 */
async function openBuilder(page: Page, info: TestInfo): Promise<void> {
    await openSection(page, info, 'Order book')
    await page.getByRole('link', {name: 'New order'}).click()
    await expect(page.getByLabel('Name', {exact: true})).toBeVisible()
}

/**
 * Adds a line through the dialog: picks the item by typing and takes it with Enter.
 *
 * @param page the page
 * @param search what is typed into the picker
 * @returns the dialog, still open
 */
async function pick(page: Page, search: string) {
    await page.getByRole('button', {name: 'Add a line'}).click()
    const dialog = page.getByRole('dialog', {name: 'Add a line'})
    await dialog.getByRole('button', {name: /Item/}).click()
    await page.getByPlaceholder('Search materials and your items').fill(search)
    await page.keyboard.press('Enter')
    return dialog
}

/** Placing an order on the dashboard: lines from the catalogue and the item editor, the quote, the refusal. */
test.describe('order builder', () => {
    test('places an order priced at the market average and opens the portfolio', async ({page}, info) => {
        await openBuilder(page, info)
        await page.getByLabel('Name', {exact: true}).fill('Fence posts')
        const dialog = await pick(page, 'oak')
        await dialog.getByRole('button', {name: 'Add', exact: true}).click()

        await expect(page.getByLabel('Price per item')).toHaveValue('1.2')
        await page.getByLabel('Amount').fill('64')

        await expect(page.getByText('80.64').first()).toBeVisible()
        await page.getByRole('button', {name: /Place order for/}).click()

        await expect(page).toHaveURL(/\/app\?placed=/)
        await expect(page.getByText('Your order Fence posts is placed.')).toBeVisible()
    })

    test('gives an item what survival can and nothing that excludes it', async ({page}, info) => {
        await openBuilder(page, info)
        const dialog = await pick(page, 'diamond_sword')

        await dialog.getByLabel('Sharpness').check()
        await expect(dialog.getByLabel('Smite')).toBeDisabled()
        await dialog.getByRole('button', {name: 'Add', exact: true}).click()

        await expect(page.getByText('Sharpness 5')).toBeVisible()
    })

    test('shows why the server would refuse the order', async ({page}, info) => {
        await openBuilder(page, info)
        await page.getByLabel('Name', {exact: true}).fill('Too much')
        const dialog = await pick(page, 'iron')
        await dialog.getByRole('button', {name: 'Add', exact: true}).click()
        await page.getByLabel('Amount').fill('600')

        await expect(page.getByText('An order may hold at most 512 items.')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Place order'})).toBeDisabled()
    })
})
