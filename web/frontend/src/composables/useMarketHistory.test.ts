/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import type {GatewayApi, MarketDetailView} from '~/api/gateway'
import {fakeApi} from '~/test/fakes'
import {useMarketHistory} from './useMarketHistory'

const STONE: MarketDetailView = {
    item: {material: 'minecraft:stone', name: 'Stone', amount: 1, icon: 'stone'},
    days: 30,
    history: [
        {day: '2026-10-20', orders: 4, items: 40, avg: 10, min: 8, max: 12},
        {day: '2026-10-21', orders: 1, items: 10},
        {day: '2026-10-22', orders: 3, items: 30, avg: 11, min: 9, max: 13},
    ],
}

/** Loading a material's price history. */
describe('the price history', () => {
    it('draws only the days with prices and loads again for another period', async () => {
        const api = fakeApi()
        const read = vi.fn().mockResolvedValue(STONE)
        api.read = read as unknown as GatewayApi['read']
        const history = useMarketHistory(ref('minecraft:stone'), api)
        await flushPromises()

        expect(history.prices.value).toEqual([10, 11])
        expect(read).toHaveBeenLastCalledWith('market.item', {material: 'minecraft:stone', days: 30})

        history.range.value = 90
        await flushPromises()
        expect(read).toHaveBeenLastCalledWith('market.item', {material: 'minecraft:stone', days: 90})
    })

    it('loads nothing while no material is open', async () => {
        const api = fakeApi()
        const read = vi.fn()
        api.read = read as unknown as GatewayApi['read']
        const history = useMarketHistory(ref(null), api)
        await flushPromises()

        expect(read).not.toHaveBeenCalled()
        expect(history.status.value).toBe('idle')
    })
})

/** Lets the pending promises settle. */
async function flushPromises(): Promise<void> {
    await new Promise(resolve => setTimeout(resolve, 0))
}
