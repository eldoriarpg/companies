/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import type {GatewayApi, TreasuryLogPage} from '~/api/gateway'
import {FakeSocket, fakeApi, serverInfo} from '~/test/fakes'
import {patch, snapshot} from '~/test/snapshots'
import {useDashboard} from './useDashboard'
import {useTreasury} from './useTreasury'

/**
 * A treasury log page with one movement.
 *
 * @param balance the balance after it
 * @returns the page
 */
function page(balance: number): TreasuryLogPage {
    return {page: 1, pages: 1, entries: [{id: balance, time: '2026-10-24T09:00:00Z', kind: 'DEPOSIT', amount: 10, balance}]}
}

beforeEach(() => {
    vi.useFakeTimers()
    useDashboard().forget()
})

afterEach(() => {
    useDashboard().forget()
    vi.useRealTimers()
})

/** The treasury's log and line. */
describe('the treasury', () => {
    it('drops the old company\'s log and loads the new one\'s after a switch', async () => {
        const api = fakeApi()
        api.server.mockResolvedValue(serverInfo({features: ['company.treasury']}))
        const read = vi.fn().mockResolvedValueOnce(page(2500)).mockResolvedValueOnce(page(90))
        api.read = read as unknown as GatewayApi['read']
        let socket: FakeSocket | null = null
        const dashboard = useDashboard({api, connect: url => {
            socket = new FakeSocket(url)
            return socket
        }})
        dashboard.start()
        await vi.advanceTimersByTimeAsync(0)
        const treasury = useTreasury(api)
        await treasury.loadLog()
        expect(treasury.recent.value.map(entry => entry.balance)).toEqual([2500])

        const other = {...snapshot().company, id: 9, name: 'Copperworks', treasury: {balance: 90}}
        ;(socket as unknown as FakeSocket).receive(patch(42, {path: 'company', op: 'REPLACE', value: other}))
        await vi.advanceTimersByTimeAsync(0)
        expect(read).toHaveBeenCalledTimes(2)
        expect(treasury.recent.value.map(entry => entry.balance)).toEqual([90])
        expect(treasury.series.value).not.toContain(2500)
    })

    it('loads the log once the treasury becomes visible after a switch', async () => {
        const api = fakeApi()
        api.server.mockResolvedValue(serverInfo({features: ['company.treasury']}))
        const read = vi.fn().mockResolvedValueOnce(page(2500)).mockResolvedValueOnce(page(90))
        api.read = read as unknown as GatewayApi['read']
        let socket: FakeSocket | null = null
        const dashboard = useDashboard({api, connect: url => {
            socket = new FakeSocket(url)
            return socket
        }})
        dashboard.start()
        await vi.advanceTimersByTimeAsync(0)
        const treasury = useTreasury(api)
        await treasury.loadLog()

        const hidden = {...snapshot().company, id: 9, name: 'Copperworks', treasury: null}
        ;(socket as unknown as FakeSocket).receive(patch(42, {path: 'company', op: 'REPLACE', value: hidden}))
        await vi.advanceTimersByTimeAsync(0)
        expect(read).toHaveBeenCalledTimes(1)
        expect(treasury.recent.value).toEqual([])

        const shown = {...hidden, treasury: {balance: 90}}
        ;(socket as unknown as FakeSocket).receive(patch(43, {path: 'company', op: 'REPLACE', value: shown}))
        await vi.advanceTimersByTimeAsync(0)
        expect(read).toHaveBeenCalledTimes(2)
        expect(treasury.recent.value.map(entry => entry.balance)).toEqual([90])
    })
})
