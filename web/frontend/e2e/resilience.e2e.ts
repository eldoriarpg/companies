/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {expect, test} from '@playwright/test'
import {CODE, logIn, openDashboard, openSection} from './session'

/** A Turnstile stand-in that passes at once, served in place of Cloudflare's script. */
const TURNSTILE = `window.turnstile = {
    render(element, options) { setTimeout(() => options.callback('turnstile-ok'), 50); return 'widget' },
    reset() {},
    remove() {},
}`

/** Logins and sessions that do not go the happy way. */
test.describe('login trouble', () => {
    test('shows the discontinued server when the code is refused for it', async ({page}) => {
        await page.goto('/DISC01')
        await page.getByLabel('Login code').fill(CODE)
        await page.getByRole('button', {name: 'Open dashboard'}).click()
        const notice = page.getByRole('alert')
        await expect(notice).toContainText('The dashboard no longer supports Mossvale SMP.')
        await expect(notice).toContainText('Companies 2.0 uses an order format the dashboard no longer reads.')
        await expect(page.getByLabel('Login code')).toHaveCount(0)
    })

    test('says when the last attempt was used', async ({page}) => {
        await page.goto('/LOCK01')
        await page.getByLabel('Login code').fill('AAAAAA')
        await page.getByRole('button', {name: 'Open dashboard'}).click()
        await expect(page.getByText('Too many wrong codes.')).toBeVisible()
        await expect(page.getByLabel('Login code')).toHaveCount(0)
    })

    test('sends the Turnstile token with the code', async ({page}) => {
        await page.route('https://challenges.cloudflare.com/**', route => route.fulfill({contentType: 'text/javascript', body: TURNSTILE}))
        await page.goto('/TURN01')
        await page.getByLabel('Login code').fill(CODE)
        await page.getByRole('button', {name: 'Open dashboard'}).click()
        await expect(page).toHaveURL(/\/app$/)
    })
})

/** A dashboard whose server is away, refuses, or ends the session. */
test.describe('session trouble', () => {
    test('keeps the navigation and warns while the server is offline', async ({page}, info) => {
        await openDashboard(page, 'OFFL01')
        await expect(page.getByText('The server is not connected right now.')).toBeVisible()
        await openSection(page, info, 'Order book')
        await expect(page.getByText('The order book could not be loaded.')).toBeVisible()
    })

    test('keeps trying while the snapshot cannot be loaded', async ({page}) => {
        await logIn(page, 'DOWN01')
        await page.goto('/app')
        await expect(page.getByText('The server cannot be reached right now. The dashboard keeps trying.')).toBeVisible()
    })

    test('goes to the login when the session ends', async ({page}) => {
        await openDashboard(page, 'KICK01')
        await expect(page).toHaveURL(/\/$/)
        await expect(page.getByLabel('Short id')).toBeVisible()
    })

    test('shows the explanation when the server is discontinued meanwhile', async ({page}) => {
        await openDashboard(page, 'DCON01')
        await expect(page.getByRole('alert')).toContainText('The dashboard no longer supports Mossvale SMP.')
    })

    test('shows no page of a feature the server does not list', async ({page}) => {
        await openDashboard(page, 'NOMK01')
        await expect(page.getByRole('link', {name: 'Market'})).toHaveCount(0)
        await page.goto('/app/market')
        await expect(page.getByText('This server does not offer this part of the dashboard.')).toBeVisible()
        await expect(page.getByTestId('market')).toHaveCount(0)
    })
})
