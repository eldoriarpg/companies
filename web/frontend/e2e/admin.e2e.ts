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
        await expect(order.getByText('Delete Harbour wall and give the owner price and fee of 1,155.00 back? Delivered items go back to the workers.')).toBeVisible()
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

    test('looks a player up and grants money', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)
        await page.getByRole('tab', {name: 'Players'}).click()

        await page.getByLabel('Player name').fill('orla')
        await page.getByRole('button', {name: 'Look up'}).click()
        const player = page.getByTestId('admin-player')
        await expect(player.getByText('Stonebridge Builders')).toBeVisible()
        await player.getByLabel('Money to grant').fill('250')
        await player.getByRole('button', {name: 'Grant money'}).click()

        await expect(player.getByText('Orla got 250.00 coins.')).toBeVisible()
    })

    test('gives a company money and dissolves it after asking', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)
        await page.getByRole('tab', {name: 'Companies'}).click()

        await page.getByLabel('Company name').fill('Stonebridge Builders')
        await page.getByRole('button', {name: 'Open', exact: true}).click()
        const treasury = page.getByTestId('admin-treasury')
        await treasury.getByLabel('Amount').fill('50')
        await treasury.getByRole('button', {name: 'Give'}).click()
        await expect(treasury.getByText('18,500.00')).toBeVisible()

        const company = page.getByTestId('admin-company')
        await company.getByRole('button', {name: 'Dissolve the company'}).click()
        await company.getByRole('button', {name: 'Dissolve', exact: true}).click()
        const dissolved = page.getByText('Stonebridge Builders is dissolved.')
        await expect(dissolved).toBeVisible()
        await expect(page.getByTestId('admin-company-done')).toBeFocused()
    })

    test('saves a level from one form, adds one and removes it', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)
        await page.getByRole('tab', {name: 'Levels'}).click()

        await page.getByTestId('admin-levels').getByRole('button', {name: '2 · Guild'}).click()
        const level = page.getByTestId('admin-level')
        await level.getByLabel('Name').fill('Grand guild')
        await level.getByLabel('Orders done').fill('45')
        await level.getByRole('button', {name: 'Save the level'}).click()
        await expect(page.getByText('Level 2 is saved.')).toBeVisible()
        await expect(page.getByTestId('admin-levels-done')).toBeFocused()
        await expect(page.getByTestId('admin-levels').getByRole('button', {name: '2 · Grand guild'})).toBeVisible()

        await page.getByRole('button', {name: 'Add a level'}).click()
        await expect(page.getByTestId('admin-level').getByLabel('Name')).toHaveValue('New level')
        await page.getByTestId('admin-level').getByRole('button', {name: 'Remove the level'}).click()
        await page.getByTestId('admin-level').getByRole('button', {name: 'Remove', exact: true}).click()
        await expect(page.getByText('The level is removed.')).toBeVisible()
        await expect(page.getByTestId('admin-levels-done')).toBeFocused()
        await expect(page.getByTestId('admin-levels').getByRole('listitem')).toHaveCount(2)
    })

    test('binds an NPC on the server the admin picks', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)
        await page.getByRole('tab', {name: 'Boards'}).click()

        await expect(page.getByTestId('admin-presets').getByText('4 open')).toBeVisible()
        await page.getByLabel('Server').selectOption({label: 'Secondary 9a2d7c41'})
        await expect(page.getByTestId('admin-presets').getByText('1 open')).toBeVisible()

        const npcs = page.getByTestId('admin-npcs')
        await npcs.getByLabel('NPC (Citizens id or FancyNpcs name)').fill('99')
        await npcs.getByRole('button', {name: 'Bind'}).click()
        await expect(npcs.getByText('Citizens has no NPC 99.')).toBeVisible()
        await npcs.getByLabel('NPC (Citizens id or FancyNpcs name)').fill('4')
        await npcs.getByLabel('Board', {exact: true}).fill('iron')
        await npcs.getByRole('button', {name: 'Bind'}).click()
        await expect(npcs.getByText('Citizens NPC 4 now opens its board.')).toBeVisible()
        await expect(npcs.getByText('Citizens 4 → iron')).toBeVisible()
        await npcs.getByRole('button', {name: 'Unbind'}).click()
        await expect(npcs.getByText('No NPC opens a board on this server.')).toBeVisible()
    })

    test('removes an old server and syncs the configuration', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)
        await page.getByRole('tab', {name: 'Servers'}).click()

        const servers = page.getByTestId('admin-servers')
        await expect(servers.getByText('survival')).toBeVisible()
        await expect(servers.getByRole('listitem')).toHaveCount(3)
        await servers.getByRole('listitem').filter({hasText: 'Secondary 0b1c2d3e'}).getByRole('button', {name: 'Remove'}).click()
        await servers.getByRole('listitem').filter({hasText: 'Secondary 0b1c2d3e'}).getByRole('button', {name: 'Remove', exact: true}).last().click()
        await expect(page.getByText('Secondary 0b1c2d3e is removed.')).toBeVisible()
        await expect(servers.getByRole('listitem')).toHaveCount(2)

        await expect(servers.getByRole('listitem').filter({hasText: 'Secondary 9a2d7c41'}).getByText('restart pending: config.yml boards.enabled')).toBeVisible()

        await page.getByRole('button', {name: 'Sync the configuration'}).click()
        await page.getByRole('button', {name: 'Sync', exact: true}).click()
        await expect(page.getByText('The configuration is synced.')).toBeVisible()
    })

    test('shows the shared settings without their secrets and what a revision changed', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)
        await page.getByRole('tab', {name: 'Settings'}).click()

        await expect(page.getByTestId('admin-settings-status')).toContainText('Revision 2 · survival uses 2')
        await expect(page.getByTestId('admin-settings-status')).toContainText('Still on an older revision: Secondary 9a2d7c41')
        await expect(page.getByTestId('admin-setting-generalSettings.language').getByRole('combobox')).toHaveValue('en_US')
        const url = page.getByTestId('admin-setting-discord.url')
        await expect(url.getByTestId('admin-setting-secret')).toHaveText('set by the environment variable on survival')
        await expect(url.getByRole('textbox')).toBeDisabled()
        await expect(page.getByTestId('admin-setting-dashboard.features').getByLabel('market', {exact: true})).not.toBeChecked()
        await expect(page.getByTestId('admin-setting-dashboard.features').getByLabel('inbox', {exact: true})).toBeChecked()
        await expect(page.getByTestId('admin-settings-general').getByText('set on survival')).toBeVisible()

        await page.getByTestId('admin-settings-history').getByRole('button', {name: /#2 · Ada/}).click()
        const revision = page.getByTestId('admin-settings-revision-2')
        await expect(revision).toContainText('discord.enabled · off → on')
        await expect(revision).toContainText('discord.url · secret changed')
        await expect(revision).toContainText('boards.presets · edited in the file only')

        await page.getByTestId('admin-settings-history').getByRole('button', {name: /#1 · startup/}).click()
        await expect(page.getByTestId('admin-settings-revision-1')).toContainText('The first revision')

        await page.getByLabel('File', {exact: true}).selectOption('user.yml')
        await expect(page.getByTestId('admin-setting-maxOrders').getByRole('textbox')).toHaveValue('7')
    })

    test('saves changed settings, marks a refused one and keeps changes when someone saved meanwhile', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)
        await page.getByRole('tab', {name: 'Settings'}).click()
        const save = page.getByTestId('admin-settings-save')
        await expect(save).toContainText('No changes to save.')

        await page.getByTestId('admin-setting-generalSettings.timezone').getByRole('textbox').fill('Mars/Base')
        await save.getByRole('button', {name: 'Save'}).click()
        await save.getByRole('button', {name: 'Save', exact: true}).last().click()
        await expect(page.getByTestId('admin-setting-generalSettings.timezone')).toContainText('Must be a time zone such as Europe/Berlin, or empty.')

        await page.getByTestId('admin-setting-generalSettings.timezone').getByRole('textbox').fill('')
        await page.getByTestId('admin-setting-generalSettings.language').getByRole('combobox').selectOption('de_DE')
        await expect(save).toContainText('Changed: Language')
        await save.getByRole('button', {name: 'Save'}).click()
        await save.getByRole('button', {name: 'Save', exact: true}).last().click()
        await expect(page.getByTestId('admin-settings-done')).toHaveText('Saved as revision 3.')
        await expect(page.getByTestId('admin-setting-generalSettings.language').getByRole('combobox')).toHaveValue('de_DE')

        await page.getByTestId('admin-setting-discord.username').getByRole('textbox').fill('Bot')
        await save.getByRole('button', {name: 'Save'}).click()
        await save.getByRole('button', {name: 'Save', exact: true}).last().click()
        await expect(save).toContainText('Revision 4 was saved meanwhile and changed: discord.username')
        await expect(page.getByTestId('admin-setting-discord.username').getByTestId('admin-setting-newer')).toHaveText('Revision 4 has: Other')
        await expect(page.getByTestId('admin-setting-discord.username').getByRole('textbox')).toHaveValue('Bot')
        await save.getByRole('button', {name: 'Save'}).click()
        await save.getByRole('button', {name: 'Save', exact: true}).last().click()
        await expect(page.getByTestId('admin-settings-done')).toHaveText('Saved as revision 5.')
    })

    test('makes an earlier revision the newest again after asking', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)
        await page.getByRole('tab', {name: 'Settings'}).click()

        await page.getByTestId('admin-settings-history').getByRole('button', {name: /#1 · startup/}).click()
        await page.getByRole('button', {name: 'Make this the newest revision again'}).click()
        await page.getByRole('button', {name: 'Roll back'}).click()
        await expect(page.getByTestId('admin-settings-done')).toHaveText('Saved as revision 3.')
        await expect(page.getByTestId('admin-settings-status')).toContainText('Revision 3')
    })

    test('refuses an emptied number at its field and discards changes', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)
        await page.getByRole('tab', {name: 'Settings'}).click()
        await page.getByLabel('File', {exact: true}).selectOption('user.yml')
        const save = page.getByTestId('admin-settings-save')
        const orders = page.getByTestId('admin-setting-maxOrders')

        await orders.getByRole('textbox').fill('')
        await save.getByRole('button', {name: 'Save'}).click()
        await save.getByRole('button', {name: 'Save', exact: true}).last().click()
        await expect(orders).toContainText('Needs a value.')
        await expect(page.getByLabel('File', {exact: true})).toBeDisabled()

        await save.getByRole('button', {name: 'Discard changes'}).click()
        await expect(save).toContainText('No changes to save.')
        await expect(orders.getByRole('textbox')).toHaveValue('7')
    })

    test('uploads an icon for every server and goes back to the server icon', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openAdmin(page, info)
        await page.getByRole('tab', {name: 'Settings'}).click()
        const icon = page.getByTestId('admin-settings-icon')
        await expect(icon).toContainText('Each server shows its own server-icon.png.')

        await icon.getByLabel('Upload an icon for every server').setInputFiles({
            name: 'icon.png',
            mimeType: 'image/png',
            buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==', 'base64'),
        })
        await expect(page.getByTestId('admin-settings-icon-preview')).toBeVisible()
        await icon.getByRole('button', {name: 'Upload for every server'}).click()
        await expect(page.getByTestId('admin-settings-done')).toHaveText('Saved as revision 3.')
        await expect(icon).toContainText('Every server shows the uploaded icon.')

        await icon.getByRole('button', {name: 'Use the server icon again'}).click()
        await expect(page.getByTestId('admin-settings-done')).toHaveText('Saved as revision 4.')
        await expect(icon).toContainText('Each server shows its own server-icon.png.')
    })
})
