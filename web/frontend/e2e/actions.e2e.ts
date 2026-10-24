/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test, type Page, type TestInfo} from '@playwright/test'
import {onPhone, openDashboard, openSection} from './session'

test.beforeEach(async ({page}) => openDashboard(page, 'CALM01'))

/**
 * The details of the open order, beside the list or in the phone's sheet.
 *
 * @param page the page
 * @param info the test, for the device
 * @returns the details
 */
function details(page: Page, info: TestInfo) {
    return onPhone(info) ? page.getByRole('dialog') : page.getByRole('complementary', {name: 'Order details'})
}

/** The actions that change something: accepting, the owner's own orders and the inbox. */
test.describe('actions', () => {
    test('accepts an order for the company', async ({page}, info) => {
        await openSection(page, info, 'Order book')
        await page.getByTestId('order-book').getByText('Beacon pyramid').click()

        await details(page, info).getByRole('button', {name: 'Accept for my company'}).click()

        await expect(details(page, info).getByText('Accepted for your company.')).toBeVisible()
        await expect(details(page, info).getByRole('button', {name: 'Accept for my company'})).toHaveCount(0)
    })

    test('shows the plugin\'s refusal when the company cannot accept', async ({page}, info) => {
        await openSection(page, info, 'Order book')
        await page.getByTestId('order-book').getByText('Library shelves').click()

        await details(page, info).getByRole('button', {name: 'Accept for my company'}).click()

        await expect(details(page, info).getByText('Your company works on as many orders as its level allows.')).toBeVisible()
    })

    test('raises an own order after showing the cost, then cancels it', async ({page}) => {
        const own = page.getByRole('region', {name: 'Your orders'}).or(page.getByLabel('Your orders')).first()
        const dome = own.getByRole('listitem').filter({hasText: 'Glass dome'})

        await dome.getByRole('button', {name: 'Raise payout'}).click()
        await dome.getByLabel('Raise by').fill('20')
        await dome.getByRole('button', {name: 'Show the cost'}).click()
        await expect(dome.getByText('The fee on the raise is 2.00; you pay 22.00 in total.')).toBeVisible()
        await dome.getByRole('button', {name: 'Pay 22.00'}).click()
        await expect(dome.getByText('Raised. You paid 22.00.')).toBeVisible()

        await dome.getByRole('button', {name: 'Cancel', exact: true}).click()
        await expect(dome.getByText('Cancel this order? The fee stays paid.')).toBeVisible()
        await dome.getByRole('button', {name: 'Cancel the order'}).click()
        await expect(dome.getByText('The order is cancelled and its price is back on your account.')).toBeVisible()
    })

    test('offers nothing on orders that are no longer open', async ({page}) => {
        const own = page.getByRole('region', {name: 'Your orders'}).or(page.getByLabel('Your orders')).first()
        await expect(own.getByRole('listitem').filter({hasText: 'Redstone lab'}).getByRole('button')).toHaveCount(0)
    })

    test('gives a position back to the order book after asking', async ({page}) => {
        const stalls = page.getByTestId('positions').getByRole('listitem').filter({hasText: 'Market stalls'})

        await stalls.getByRole('button', {name: 'Give back'}).click()
        await expect(stalls.getByText('Give Market stalls back to the order book?')).toBeVisible()
        await stalls.getByRole('button', {name: 'Give the order back'}).click()

        await expect(stalls.getByText('Market stalls is back in the order book.')).toBeVisible()
    })

    test('marks the inbox read and mutes a category', async ({page}, info) => {
        await openSection(page, info, 'Inbox')

        await page.getByRole('button', {name: 'Mark all read'}).click()
        await expect(page.getByText('3 marked read.')).toBeVisible()

        const orders = page.getByRole('switch', {name: 'Mute orders'})
        await expect(orders).toHaveAttribute('aria-checked', 'false')
        await orders.click()
        await expect(orders).toHaveAttribute('aria-checked', 'true')
    })
})
