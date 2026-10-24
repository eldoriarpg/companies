/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test, type Page} from '@playwright/test'
import {openDashboard, openSection} from './session'

test.beforeEach(async ({page}, info) => {
    await openDashboard(page, 'CALM01')
    await openSection(page, info, 'Company')
})

/**
 * The members panel.
 *
 * @param page the page
 * @returns the panel
 */
function members(page: Page) {
    return page.getByRole('region', {name: 'Members'}).or(page.getByLabel('Members')).first()
}

/** Managing the company as its owner: members, applications, the profile and the treasury. */
test.describe('company management', () => {
    test('changes the permissions of a member and removes another after asking', async ({page}) => {
        const list = members(page)
        await list.getByRole('button', {name: 'mossycobble'}).click()
        await list.getByRole('switch', {name: 'Kick'}).click()
        await list.getByRole('button', {name: 'Save permissions'}).click()
        await expect(list.getByText('The permissions of mossycobble are saved.')).toBeVisible()

        await list.getByRole('button', {name: 'Tidewalker'}).click()
        await list.getByRole('button', {name: 'Remove from the company'}).click()
        await expect(list.getByText('Remove Tidewalker from the company?')).toBeVisible()
        await expect(list.getByRole('button', {name: 'Remove', exact: true})).toBeFocused()
        await list.getByRole('button', {name: 'Remove', exact: true}).click()
        await expect(list.getByText('Tidewalker is no longer a member.')).toBeVisible()
    })

    test('offers the owner no way to leave and nothing on their own row', async ({page}) => {
        const list = members(page)
        await expect(list.getByRole('button', {name: 'Leave the company'})).toHaveCount(0)
        await expect(list.getByRole('button', {name: 'Wren'})).toHaveCount(0)
    })

    test('answers applications and takes back an invite', async ({page}) => {
        const panel = page.getByRole('region', {name: 'Applications'}).or(page.getByLabel('Applications', {exact: true})).first()
        const applications = page.getByTestId('applications')
        await expect(applications.getByRole('listitem')).toHaveCount(2)

        await applications.getByRole('listitem').filter({hasText: 'Juniper'}).getByRole('button', {name: 'Accept'}).click()
        await expect(panel.getByText('Juniper is now a member.')).toBeVisible()
        await expect(applications.getByRole('listitem')).toHaveCount(1)

        await page.getByTestId('invites').getByRole('button', {name: 'Take back'}).click()
        await expect(panel.getByText('The invite of Corvid is taken back.')).toBeVisible()
        await expect(page.getByTestId('invites')).toHaveCount(0)
    })

    test('edits the profile and shows the plugin\'s refusal', async ({page}) => {
        await page.getByRole('button', {name: 'Edit profile'}).click()
        await page.getByLabel('Tag', {exact: true}).fill('BUILDERS')
        await page.getByRole('button', {name: 'Save profile'}).click()
        await expect(page.getByText('The tag may have at most 5 characters.')).toBeVisible()

        await page.getByLabel('Tag', {exact: true}).fill('SBB')
        await page.getByLabel('Colour').selectOption('dark_aqua')
        await page.getByLabel('Joining').selectOption('OPEN')
        await page.getByRole('button', {name: 'Save profile'}).click()
        await expect(page.getByText('The profile is saved.')).toBeVisible()
    })

    test('backs out of removing a member with the focus on the button again', async ({page}) => {
        const list = members(page)
        await list.getByRole('button', {name: 'Tidewalker'}).click()
        await list.getByRole('button', {name: 'Remove from the company'}).click()
        await list.getByRole('button', {name: 'Back'}).click()
        await expect(list.getByRole('button', {name: 'Remove from the company'})).toBeFocused()
    })

    test('renames the company after naming the price', async ({page}) => {
        await page.getByRole('button', {name: 'Rename'}).click()
        await page.getByLabel('New name').fill('Harbour Builders')
        await page.getByRole('button', {name: 'Show the cost'}).click()
        await expect(page.getByText('Renaming to Harbour Builders costs 5,000.00.')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Pay 5,000.00 yourself'})).toBeFocused()

        await page.getByRole('button', {name: 'Pay from the treasury'}).click()
        await expect(page.getByText('The company is now called Harbour Builders.')).toBeVisible()
    })

    test('moves money through the treasury and sets the share', async ({page}) => {
        await expect(page.getByText('Keeps 5% of every payout.')).toBeVisible()
        await page.getByLabel('Amount').fill('250,50')
        await page.getByRole('button', {name: 'Pay in'}).click()
        await expect(page.getByText('You paid in 250.50. The treasury holds 18,700.50.')).toBeVisible()

        await page.getByLabel('Amount').fill('99999')
        await page.getByRole('button', {name: 'Take out'}).click()
        await expect(page.getByText('The treasury does not hold that much.')).toBeVisible()

        await page.getByLabel(/Share of payouts/).fill('30')
        await page.getByRole('button', {name: 'Set share'}).click()
        await expect(page.getByText('The share may be at most 25%.')).toBeVisible()
        await page.getByLabel(/Share of payouts/).fill('12')
        await page.getByRole('button', {name: 'Set share'}).click()
        await expect(page.getByText('The company now keeps 12% of every payout.')).toBeVisible()
    })
})
