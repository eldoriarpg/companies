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
