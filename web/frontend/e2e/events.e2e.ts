/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test, type Page, type TestInfo} from '@playwright/test'
import {onPhone, openDashboard, openSection} from './session'

/**
 * Opens the Events page: from the sidebar on a desktop, from the menu on a phone.
 *
 * @param page the page
 * @param info the test, for the device
 */
async function openEvents(page: Page, info: TestInfo): Promise<void> {
    if (onPhone(info)) {
        await page.getByRole('button', {name: 'Menu'}).click()
        await page.getByRole('dialog').getByRole('link', {name: 'Events'}).click()
    } else {
        await page.getByRole('navigation', {name: 'Sections'}).filter({visible: true}).getByRole('link', {name: 'Events'}).click()
    }
}

/** The Events page and the chunks of event offers in the order book. */
test.describe('event offers', () => {
    test('list the running offers with their progress and lead the ticker', async ({page}, info) => {
        await openDashboard(page, 'EVNT01')
        await expect(page.getByTestId('ticker-offer').filter({hasText: 'Bridge logs'})).toBeVisible()
        await openEvents(page, info)

        const events = page.getByTestId('events')
        await expect(events.getByRole('listitem')).toHaveCount(2)
        await expect(events.getByText('2,250 of 5,000 items (45 %)')).toBeVisible()
        await expect(events.getByText('Contest')).toBeVisible()
    })

    test('list the running server events with their effect and lead the ticker', async ({page}, info) => {
        await openDashboard(page, 'EVNT01')
        await expect(page.getByTestId('ticker-server-event').filter({hasText: 'Harvest week'})).toBeVisible()
        await openEvents(page, info)

        const server = page.getByTestId('server-events')
        await expect(server.getByText('Harvest week')).toBeVisible()
        await expect(server.getByText('×1.5 on payouts')).toBeVisible()
        await expect(server.getByText('Items: WHEAT')).toBeVisible()
        await expect(page.getByTestId('events').getByText('Part of Harvest week')).toBeVisible()
    })

    test('show a goal\'s contributors without naming players alone and lead to its orders', async ({page}, info) => {
        await openDashboard(page, 'EVNT01')
        await openEvents(page, info)
        await page.getByTestId('events').getByText('Bridge logs').click()

        const detail = onPhone(info) ? page.getByRole('dialog') : page.getByTestId('event-detail')
        const contributors = detail.getByTestId('event-contributors')
        await expect(contributors.getByText('Stonebridge Builders')).toBeVisible()
        await expect(contributors.getByText('A player working alone')).toBeVisible()
        await detail.getByTestId('event-orders').click()

        await expect(page.getByTestId('offer-filter')).toHaveText('Offer: Bridge logs ×')
        const book = page.getByTestId('order-book')
        await expect(book.getByRole('listitem')).toHaveCount(1)
        await expect(book.getByTestId('event-badge')).toBeVisible()
        await page.getByTestId('offer-filter').click()
        await expect(book.getByRole('listitem')).toHaveCount(12)
    })

    test('let a member who manages orders join and leave a contest; delivering stays in game', async ({page}, info) => {
        await openDashboard(page, 'EVNT01')
        await openEvents(page, info)
        await page.getByTestId('events').getByText('Log race').click()

        const detail = onPhone(info) ? page.getByRole('dialog') : page.getByTestId('event-detail')
        await expect(detail.getByText('/company contest deliver 42')).toBeVisible()
        await expect(detail.getByText('Your company does not take part.')).toBeVisible()
        await detail.getByTestId('event-join').click()
        await expect(detail.getByText('Your company takes part.')).toBeVisible()
        await expect(detail.getByTestId('event-ranking').getByText('Stonebridge Builders')).toBeVisible()
        await detail.getByTestId('event-leave').click()
        await expect(detail.getByText('Your company does not take part.')).toBeVisible()
    })

    test('show a closed contest with its final ranking and prize winners and no buttons', async ({page}, info) => {
        await openDashboard(page, 'EVNT01')
        await page.goto('/app/events?offer=43')

        const detail = onPhone(info) ? page.getByRole('dialog') : page.getByTestId('event-detail')
        await expect(detail.getByTestId('event-closed')).toHaveText('This offer is closed (Ended). What is shown is final.')
        await expect(detail.getByText('Final ranking')).toBeVisible()
        await expect(detail.getByTestId('event-prizes').getByText(/Copperworks · paid/)).toBeVisible()
        await expect(detail.getByTestId('event-join')).toHaveCount(0)
        await expect(detail.getByTestId('event-leave')).toHaveCount(0)
        await expect(detail.getByText('/company contest deliver 43')).toHaveCount(0)
    })

    test('read a contest again when joining was refused as joined already', async ({page}, info) => {
        await openDashboard(page, 'EVNT01')
        await page.goto('/app/events?offer=44')

        const detail = onPhone(info) ? page.getByRole('dialog') : page.getByTestId('event-detail')
        await detail.getByTestId('event-join').click()
        await expect(detail.getByText('Your company takes part in contest #44 already.')).toBeVisible()
        await expect(detail.getByText('Your company takes part.')).toBeVisible()
        await expect(detail.getByTestId('event-leave')).toBeVisible()
    })

    test('are found alone in the order book and carry their badge', async ({page}, info) => {
        await openDashboard(page, 'EVNT01')
        await openSection(page, info, 'Order book')
        const book = page.getByTestId('order-book')
        await expect(book.getByTestId('event-badge')).toHaveCount(1)

        await page.getByRole('button', {name: 'Event offers'}).click()
        await expect(book.getByRole('listitem')).toHaveCount(1)
        await book.getByText('Firewood').click()
        const details = onPhone(info) ? page.getByRole('dialog') : page.getByRole('complementary', {name: 'Order details'})
        await expect(details.getByTestId('order-offer').getByText('Bridge logs')).toBeVisible()
    })

    test('are not offered where the server has no Events page', async ({page}, info) => {
        await openDashboard(page, 'CALM01')
        await openSection(page, info, 'Order book')
        await expect(page.getByRole('button', {name: 'Event offers'})).toHaveCount(0)
        await expect(page.getByTestId('event-badge')).toHaveCount(0)
        await expect(page.getByRole('link', {name: 'Events'})).toHaveCount(0)
    })
})
