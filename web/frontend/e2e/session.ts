/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {expect, type Page, type TestInfo} from '@playwright/test'

/** The code every waiting session of the mock gateway takes. */
export const CODE = 'KXT4PR'

/**
 * Logs the page's browser in to a waiting session of the mock gateway, as the code form would, and opens the
 * dashboard.
 *
 * @param page the page
 * @param shortId the session, `MOSS01` for a supported server
 */
export async function openDashboard(page: Page, shortId = 'MOSS01'): Promise<void> {
    const answer = await page.request.post(`/api/v1/s/${shortId}/login`, {data: {code: CODE}})
    expect(answer.status()).toBe(204)
    await page.goto('/app')
    await expect(page.getByTestId('treasury-balance')).toBeVisible()
}

/**
 * Whether a story runs in one of the phone profiles.
 *
 * @param info the story's info
 * @returns true on a phone
 */
export function onPhone(info: TestInfo): boolean {
    return info.project.name === 'pixel' || info.project.name === 'iphone'
}

/**
 * Opens a section the way the reader would: from the sidebar on a desktop, from the tab bar or the menu on a phone.
 *
 * @param page the page
 * @param info the story's info
 * @param name the section's name in the navigation
 */
export async function openSection(page: Page, info: TestInfo, name: string): Promise<void> {
    const navigation = page.getByRole('navigation', {name: 'Sections'})
    if (onPhone(info) && name === 'Rankings') {
        await page.getByRole('button', {name: 'Menu'}).click()
        await page.getByRole('dialog').getByRole('link', {name}).click()
    } else {
        await navigation.filter({visible: true}).getByRole('link', {name}).click()
    }
}
