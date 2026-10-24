/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import type {ApplicationsPage, GatewayApi} from '~/api/gateway'
import {fakeApi} from '~/test/fakes'
import {useApplications} from './useApplications'

const PAGE: ApplicationsPage = {page: 1, pages: 1, total: 0, entries: [], invites: []}

/** Loading the applications to the company. */
describe('the applications', () => {
    it('keep only the newest answer', async () => {
        const api = fakeApi()
        let first: (page: ApplicationsPage) => void = () => {}
        api.read = vi.fn()
            .mockReturnValueOnce(new Promise(resolve => {
                first = resolve
            }))
            .mockResolvedValueOnce({...PAGE, total: 2}) as unknown as GatewayApi['read']
        const applications = useApplications(api)

        const overtaken = applications.load()
        await applications.load()
        first({...PAGE, total: 1})
        await overtaken

        expect(applications.result.value?.total).toBe(2)
        expect(applications.status.value).toBe('ready')
    })

    it('say when they cannot be loaded', async () => {
        const api = fakeApi()
        api.read = vi.fn().mockRejectedValue(new Error('offline')) as unknown as GatewayApi['read']
        const applications = useApplications(api)

        await applications.load()

        expect(applications.status.value).toBe('failed')
    })
})
