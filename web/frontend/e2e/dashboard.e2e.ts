/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {expect, test} from '@playwright/test'
import {onPhone, openDashboard, openSection} from './session'

test.beforeEach(async ({page}) => openDashboard(page))

/** The sections of the dashboard, read only, on a desktop and on a phone. */
test.describe('dashboard', () => {
    test('shows the portfolio and updates it live from two nodes and a resync', async ({page}) => {
        const positions = page.getByTestId('positions')
        await expect(positions.getByText('Market stalls')).toBeVisible()
        await expect(page.getByText('Live', {exact: true}).filter({visible: true})).toBeVisible()

        const activity = page.getByTestId('activity')
        await expect(activity.getByText('Kestrel_')).toBeVisible()
        await expect(activity.getByText('delivered 64 Spruce Planks to Market stalls')).toBeVisible()
        await expect(activity.getByText('1,150.00 paid into the treasury')).toBeVisible()
        await expect(positions.getByRole('progressbar', {name: 'Market stalls filled'})).toHaveAttribute('aria-valuenow', '90')
        await expect(page.getByTestId('treasury-balance')).toHaveText('19,600.00')
        const inbox = page.getByRole('navigation', {name: 'Sections'}).filter({visible: true}).getByRole('link', {name: /Inbox/})
        await expect(inbox).toContainText('3')
        await expect(page.getByText('Live', {exact: true}).filter({visible: true})).toBeVisible()

        await page.getByLabel('Search positions').fill('roof')
        await expect(positions.getByText('Castle roof')).toBeVisible()
        await expect(positions.getByText('Market stalls')).toHaveCount(0)
        await expect(page.getByText('Garden lights')).toBeVisible()
        const movements = page.getByRole('list', {name: 'Latest treasury movements'})
        await expect(movements.getByRole('listitem')).toHaveCount(5)
        await expect(movements.getByText('Kestrel_').first()).toBeVisible()
        await expect(movements.getByText('▼ 200.00')).toBeVisible()
        await expect(movements.getByText('▲ 550.00')).toBeVisible()
        await expect(page.getByText('120 items to collect in game')).toBeVisible()
    })

    test('searches the order book and opens an order', async ({page}, info) => {
        await openSection(page, info, 'Order book')
        const book = page.getByTestId('order-book')
        await expect(book.getByText('Spawn walls')).toBeVisible()

        await page.getByLabel('Search open orders').fill('beacon')
        await expect(book.getByText('Spawn walls')).toHaveCount(0)
        await book.getByText('Beacon pyramid').click()
        await expect(page).toHaveURL(/order=2/)

        const details = onPhone(info) ? page.getByRole('dialog') : page.getByRole('complementary', {name: 'Order details'})
        await expect(details.getByText('The owner stays anonymous')).toBeVisible()
        await expect(details.getByText('1,476')).toBeVisible()
        await expect(details.getByText('Open for 47 more hours')).toBeVisible()

        await page.goBack()
        await expect(page).not.toHaveURL(/order=/)
        if (onPhone(info)) await expect(page.getByRole('dialog')).toHaveCount(0)
    })

    test('shows the market with its prices', async ({page}, info) => {
        await openSection(page, info, 'Market')
        const market = page.getByTestId('market')
        await expect(market.getByText('Diamond')).toBeVisible()
        await expect(market.getByText('96.00').first()).toBeVisible()
        await page.getByLabel('Search items').fill('oak')
        await expect(market.getByText('Oak Log')).toBeVisible()
        await expect(market.getByText('Diamond')).toHaveCount(0)
    })

    test('shows the company and finds a member', async ({page}, info) => {
        await openSection(page, info, 'Company')
        await expect(page.getByTestId('company-treasury')).toHaveText(/^(18,450|19,600)\.00$/)
        const members = page.getByTestId('members')
        await expect(members.getByRole('listitem')).toHaveCount(14)
        await page.getByLabel('Search members').fill('kes')
        await expect(members.getByRole('listitem')).toHaveCount(1)
        await expect(members.getByText('Kestrel_')).toBeVisible()
        await expect(page.getByText('Inviting a player is done in game.')).toBeVisible()
    })

    test('shows the inbox and the rankings', async ({page}, info) => {
        await openSection(page, info, 'Inbox')
        const inbox = page.getByTestId('inbox')
        await expect(inbox.getByText('Your order Redstone lab is being worked on.')).toBeVisible()
        await page.getByLabel('Search inbox').fill('garden')
        await expect(inbox.getByRole('listitem')).toHaveCount(1)

        await openSection(page, info, 'Rankings')
        const rankings = page.getByTestId('rankings')
        await expect(rankings.getByText('Copperworks')).toBeVisible()
        await expect(rankings.locator('[aria-current="true"]')).toContainText('Stonebridge Builders')
        await page.getByLabel('Search companies on this page').fill('copper')
        await expect(rankings.getByRole('listitem')).toHaveCount(1)
    })

    test('draws no rounded corner', async ({page}) => {
        const rounded = await page.evaluate(() => [...document.querySelectorAll('body *')]
            .filter(element => getComputedStyle(element).borderTopLeftRadius !== '0px')
            .map(element => element.tagName))
        expect(rounded).toEqual([])
    })
})

/** What a phone needs: the tab bar and targets a thumb can hit. */
test.describe('on a phone', () => {
    test('has a tab bar with large targets', async ({page}, info) => {
        test.skip(!onPhone(info), 'phones only')
        const tabs = page.getByRole('navigation', {name: 'Sections'}).filter({visible: true}).getByRole('link')
        await expect(tabs).toHaveCount(5)
        for (const target of [...await tabs.all(), page.getByRole('button', {name: 'Menu'}), page.getByLabel('Search positions')]) {
            const box = await target.boundingBox()
            expect(box?.height ?? 0).toBeGreaterThanOrEqual(44)
        }
        const width = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
        expect(width).toBeLessThanOrEqual(0)
    })
})
