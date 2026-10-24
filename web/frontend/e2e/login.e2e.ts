/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {expect, test} from '@playwright/test'
import {CODE, onPhone, openDashboard} from './session'

/** Logging in with the short id and the code the chat showed, and logging out again. */
test.describe('login', () => {
    test('asks for the short id, then for the code, and opens the dashboard', async ({page}) => {
        await page.goto('/')
        await expect(page.getByRole('heading', {name: 'Open your dashboard'})).toBeVisible()
        await page.getByLabel('Short id').fill('calm01')
        await page.getByRole('button', {name: 'Continue'}).click()

        await expect(page).toHaveURL(/\/CALM01$/)
        await expect(page.getByRole('heading', {name: 'Session for Mossvale SMP is waiting'})).toBeVisible()
        await expect(page).toHaveTitle(/Mossvale SMP/)

        const code = page.getByLabel('Login code')
        await code.fill('AAAAAA')
        await page.getByRole('button', {name: 'Open dashboard'}).click()
        await expect(page.getByText('That code is not right.')).toBeVisible()
        await expect(page.getByText(/4 attempts left/)).toBeVisible()

        await code.fill(CODE)
        await page.getByRole('button', {name: 'Open dashboard'}).click()
        await expect(page).toHaveURL(/\/app$/)
        await expect(page.getByTestId('treasury-balance')).toHaveText('18,450.00')
        await expect(page).toHaveTitle('Portfolio - Mossvale SMP')
    })

    test('says when a link has expired', async ({page}) => {
        await page.goto('/NOPE00')
        await expect(page.getByText(/This link has expired or does not exist/)).toBeVisible()
        await expect(page.getByLabel('Login code')).toHaveCount(0)
    })

    test('sends a browser without a session from the dashboard to the login', async ({page}) => {
        await page.goto('/app')
        await expect(page).toHaveURL(/\/$/)
        await expect(page.getByLabel('Short id')).toBeVisible()
    })

    test('logs out', async ({page}, info) => {
        await openDashboard(page)
        if (onPhone(info)) {
            await page.getByRole('button', {name: 'Menu'}).click()
            await page.getByRole('dialog').getByRole('button', {name: 'Log out'}).click()
        } else {
            await page.getByRole('button', {name: 'Log out'}).click()
        }
        await expect(page).toHaveURL(/\/$/)
        await page.goto('/app')
        await expect(page.getByLabel('Short id')).toBeVisible()
    })
})
