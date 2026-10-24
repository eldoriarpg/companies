/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test} from '@playwright/test'
import {logIn, openDashboard, openSection} from './session'

/** Founding, handing over and dissolving a company on the dashboard. */
test.describe('company lifecycle', () => {
    test('founds a company after checking the name and the price', async ({page}, info) => {
        await logIn(page, 'LONE01')
        await page.goto('/app')
        await openSection(page, info, 'Company')

        await page.getByLabel('Name of the company').fill('Quarry Folk')
        await page.getByRole('button', {name: 'Check name and price'}).click()
        await expect(page.getByText('Quarry Folk is free; founding it costs 5,000.00.')).toBeVisible()
        await page.getByRole('button', {name: 'Found for 5,000.00'}).click()

        await expect(page.getByText('You founded Quarry Folk.')).toBeVisible()
    })

    test('hands the company to a member after asking', async ({page}, info) => {
        await openDashboard(page, 'CALM01')
        await openSection(page, info, 'Company')
        const list = page.getByTestId('members').locator('..')

        await list.getByRole('button', {name: 'Kestrel_'}).click()
        await list.getByRole('button', {name: 'Hand the company over'}).click()
        await expect(list.getByText('Make Kestrel_ the owner? You stay a member without owner rights.')).toBeVisible()
        await list.getByRole('button', {name: 'Hand it over'}).click()

        await expect(page.getByText('Kestrel_ owns the company now.')).toBeVisible()
    })

    test('dissolves the company only once its name is typed', async ({page}, info) => {
        await openDashboard(page, 'CALM01')
        await openSection(page, info, 'Company')

        await page.getByRole('button', {name: 'Dissolve the company'}).click()
        const dialog = page.getByRole('dialog', {name: 'Dissolve Stonebridge Builders'})
        const confirm = dialog.getByRole('button', {name: 'Dissolve', exact: true})
        await expect(dialog.getByLabel('Type Stonebridge Builders to confirm')).toBeFocused()
        await expect(confirm).toBeDisabled()
        await dialog.getByLabel('Type Stonebridge Builders to confirm').fill('stonebridge builders')
        await confirm.click()

        await expect(page.getByText('The company is dissolved.')).toBeVisible()
    })
})
