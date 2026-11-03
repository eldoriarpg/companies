/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {expect, test, type Page, type TestInfo} from '@playwright/test'
import {onPhone, openDashboard} from './session'

/**
 * Opens a section from the sidebar on a desktop and from the menu on a phone.
 *
 * @param page the page
 * @param info the test, for the device
 * @param name the section's name
 */
async function openFromMenu(page: Page, info: TestInfo, name: string): Promise<void> {
    if (onPhone(info)) {
        await page.getByRole('button', {name: 'Menu'}).click()
        await page.getByRole('dialog').getByRole('link', {name}).click()
    } else {
        await page.getByRole('navigation', {name: 'Sections'}).filter({visible: true}).getByRole('link', {name}).click()
    }
}

/**
 * @param page the page
 * @param info the test, for the device
 * @returns where a job's page shows: beside the lists on a desktop, in a sheet on a phone
 */
function jobPage(page: Page, info: TestInfo) {
    return onPhone(info) ? page.getByRole('dialog') : page.getByTestId('job-detail')
}

/** The Jobs page: the company's, open and own jobs, posting a contract and the steps of a contract's life. */
test.describe('jobs', () => {
    test('list the company\'s, the open and the player\'s own jobs', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')

        await expect(page.getByTestId('jobs-company').getByText('Cellar')).toBeVisible()
        await expect(page.getByTestId('jobs-open').getByText('Paint the barn')).toBeVisible()
        await expect(page.getByTestId('jobs-mine').getByText('Fence the field')).toBeVisible()
    })

    test('post a contract with its description', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        await page.getByTestId('contract-open').click()

        const form = page.getByTestId('contract-form')
        await form.getByLabel('Title').fill('Build a well')
        await form.getByLabel('What should be done').fill('Stone, three blocks deep.')
        await form.getByLabel('Price').fill('90')
        await form.getByTestId('contract-post').click()

        await expect(page.getByTestId('jobs-mine').getByText('Build a well')).toBeVisible()
        await expect(jobPage(page, info).getByTestId('job-description')).toHaveText('Stone, three blocks deep.')
    })

    test('save a contract as a draft, change it and post it', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        await page.getByTestId('contract-open').click()

        const form = page.getByTestId('contract-form')
        await form.getByLabel('Title').fill('Build a well')
        await form.getByLabel('What should be done').fill('Stone, three blocks deep.')
        await form.getByLabel('Price').fill('90')
        await form.getByTestId('contract-draft').click()

        await expect(page.getByTestId('jobs-mine').getByText('Build a well')).toBeVisible()
        const detail = jobPage(page, info)
        await expect(detail.getByTestId('job-draft')).toBeVisible()
        await detail.getByTestId('job-edit').click()
        await detail.getByLabel('Title').fill('Build a deep well')
        await detail.getByLabel('Hours to finish once taken').fill('48')
        await detail.getByTestId('job-edit').click()
        await expect(page.getByTestId('jobs-mine').getByText('Build a deep well')).toBeVisible()
        await detail.getByTestId('job-post').click()
        await expect(detail.getByTestId('job-draft')).toHaveCount(0)
        await expect(detail.getByTestId('job-cancel')).toBeVisible()
    })

    test('delete a draft and it leaves the list', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        await page.getByTestId('contract-open').click()
        const form = page.getByTestId('contract-form')
        await form.getByLabel('Title').fill('Dig a pond')
        await form.getByLabel('What should be done').fill('Round, two deep.')
        await form.getByLabel('Price').fill('40')
        await form.getByTestId('contract-draft').click()
        await expect(page.getByTestId('jobs-mine').getByText('Dig a pond')).toBeVisible()

        await jobPage(page, info).getByTestId('job-remove').click()

        await expect(page.getByTestId('jobs-mine').getByText('Dig a pond')).toHaveCount(0)
    })

    test('post a terraform job on a stored area', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        const areas = page.getByTestId('job-areas')
        await areas.getByTestId('job-areas-open-field').click()

        const form = areas.getByTestId('job-areas-form')
        await form.getByLabel('Kind').selectOption('terraform')
        await form.getByLabel('Title').fill('Level the field')
        await form.getByLabel('Price').fill('120')
        await form.getByLabel('Height of the top solid block').fill('68')
        await form.getByTestId('job-areas-below-materials').getByRole('checkbox').check()
        await form.getByTestId('job-areas-post').click()

        await expect(page.getByTestId('jobs-mine').getByText('Level the field')).toBeVisible()
    })

    test('post a terraform job whose fill the owner supplies and whose dug blocks go to the stock', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        const areas = page.getByTestId('job-areas')
        await areas.getByTestId('job-areas-open-field').click()

        const form = areas.getByTestId('job-areas-form')
        await form.getByLabel('Kind').selectOption('terraform')
        await form.getByLabel('Title').fill('Level the hill')
        await form.getByLabel('Price').fill('100')
        await form.getByLabel('Height of the top solid block').fill('68')
        await expect(form.getByTestId('job-areas-cost')).toContainText('150')
        await expect(form.getByTestId('job-areas-post')).toBeDisabled()
        await form.getByTestId('job-areas-fill').getByRole('checkbox').check()
        await expect(form.getByTestId('job-areas-below-materials')).toBeHidden()
        await form.getByTestId('job-areas-dug').getByRole('checkbox').check()
        await form.getByTestId('job-areas-post').click()

        const detail = jobPage(page, info)
        await expect(detail.getByTestId('job-materials')).toContainText('dirt')
        await expect(detail.getByTestId('job-missing')).toContainText('150')
    })

    test('place a schematic on a stored area and post a build job below its cost', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        const areas = page.getByTestId('job-areas')
        await areas.getByTestId('job-areas-open-field').click()

        const form = areas.getByTestId('job-areas-form')
        await expect(form.getByTestId('area-map')).toBeVisible()
        await form.getByLabel('Kind').selectOption('build')
        await form.getByLabel('Schematic').selectOption('barn')
        await form.getByLabel('Turned').selectOption('90')
        await expect(form.getByTestId('job-areas-fit')).toContainText('3×4×5')
        await form.getByTestId('area-map').focus()
        await page.keyboard.press('ArrowRight')
        await expect(form.getByTestId('area-map-footprint')).toBeVisible()
        await form.getByLabel('Title').fill('A small barn')
        await form.getByLabel('Price').fill('100')
        await expect(form.getByTestId('job-areas-post')).toBeDisabled()
        await form.getByTestId('job-areas-below').getByRole('checkbox').check()
        await form.getByTestId('job-areas-post').click()

        await expect(page.getByTestId('jobs-mine').getByText('A small barn')).toBeVisible()
    })

    test('save a supplied build job, take its materials from the returns and post it', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        const areas = page.getByTestId('job-areas')
        await areas.getByTestId('job-areas-open-field').click()

        const form = areas.getByTestId('job-areas-form')
        await form.getByLabel('Kind').selectOption('build')
        await form.getByLabel('Schematic').selectOption('barn')
        await form.getByTestId('area-map').focus()
        await page.keyboard.press('ArrowRight')
        await form.getByLabel('Title').fill('A supplied barn')
        await form.getByLabel('Price').fill('100')
        await form.getByTestId('job-areas-supplied').getByRole('checkbox').check()
        await expect(form.getByTestId('job-areas-below')).toBeHidden()
        await expect(form.getByTestId('job-areas-unmakeable')).toContainText('spawner')
        await form.getByTestId('job-areas-post').click()

        const detail = jobPage(page, info)
        await expect(detail.getByTestId('job-missing')).toContainText('40')
        await expect(detail.getByTestId('job-post')).toBeDisabled()
        await detail.getByTestId('job-inventory').click()
        await expect(detail.getByTestId('job-missing')).not.toContainText('glass')
        await detail.getByTestId('job-supply').click()
        await expect(detail.getByTestId('job-missing')).toContainText('Nothing is missing')
        await expect(detail.getByTestId('job-supply')).toBeHidden()
        await detail.getByTestId('job-post').click()

        await expect(detail.getByTestId('job-missing')).toBeHidden()
    })

    test('take an open contract for the company and say it is done', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        await page.getByTestId('jobs-open').getByText('Paint the barn').click()

        const detail = jobPage(page, info)
        await detail.getByTestId('job-take').click()
        await expect(detail.getByTestId('job-done')).toBeVisible()
        await detail.getByTestId('job-done').click()

        await expect(detail.getByTestId('job-approve-at')).toBeVisible()
    })

    test('approve a contract the company says is done', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        await page.getByTestId('jobs-mine').getByText('Fence the field').click()

        const detail = jobPage(page, info)
        await detail.getByTestId('job-approve').click()

        await expect(page.getByTestId('jobs-mine').getByText('done and paid')).toBeVisible()
        await expect(detail.getByTestId('job-approve')).toBeHidden()
    })

    test('dispute work with a reason', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        await page.getByTestId('jobs-mine').getByText('Fence the field').click()

        const detail = jobPage(page, info)
        await detail.getByTestId('job-dispute').click()
        await detail.getByLabel('Why you dispute it').fill('The gate is missing')
        await detail.getByTestId('job-dispute').click()

        await expect(detail.getByTestId('job-dispute-reason')).toHaveText('Disputed: The gate is missing. An admin settles it.')
        await expect(detail.getByTestId('job-withdraw')).toBeVisible()
    })

    test('grant the company more time it asks for', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        await page.getByTestId('jobs-mine').getByText('Roof the hall').click()

        const detail = jobPage(page, info)
        await expect(detail.getByTestId('job-extension-request')).toHaveText('The company asks for 24 more hours: The hall is bigger than drawn.')
        await detail.getByTestId('job-grant').click()

        await expect(detail.getByTestId('job-extended')).toHaveText('Extended by 24 hours')
        await expect(detail.getByTestId('job-grant')).toBeHidden()
    })

    test('give a job more hours as its owner', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        await page.getByTestId('jobs-mine').getByText('Roof the hall').click()

        const detail = jobPage(page, info)
        await detail.getByTestId('job-extend').click()
        await detail.getByTestId('job-hours').getByRole('textbox').fill('12')
        await detail.getByTestId('job-extend').click()

        await expect(detail.getByTestId('job-extended')).toHaveText('Extended by 12 hours')
    })

    test('ask the owner for more time for the company', async ({page}, info) => {
        await openDashboard(page, 'JOBS01')
        await openFromMenu(page, info, 'Jobs')
        await page.getByTestId('jobs-company').getByText('Cellar').click()

        const detail = jobPage(page, info)
        await detail.getByTestId('job-ask').click()
        await detail.getByTestId('job-hours').getByRole('textbox').fill('8')
        await detail.getByLabel('Why you need more time').fill('Bedrock is uneven')
        await detail.getByTestId('job-ask').click()

        await expect(detail.getByTestId('job-extension-request')).toHaveText('You asked for 8 more hours; the owner has not answered yet')
        await expect(detail.getByTestId('job-ask')).toBeHidden()
    })

    test('let an admin settle a dispute', async ({page}, info) => {
        await openDashboard(page, 'ADMN01')
        await openFromMenu(page, info, 'Admin')
        await page.getByRole('tab', {name: 'Disputes'}).click()

        const disputes = page.getByTestId('admin-disputes')
        await expect(disputes.getByText('The owner Orla opened it: Nothing was built.')).toBeVisible()
        await disputes.getByLabel('Percent for the company').fill('25')
        await disputes.getByTestId('dispute-settle-31').click()

        await expect(disputes.getByText('No job is disputed.')).toBeVisible()
    })
})
