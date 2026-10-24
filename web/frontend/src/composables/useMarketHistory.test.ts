/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import type {GatewayApi, MarketDetailView} from '~/api/gateway'
import {fakeApi} from '~/test/fakes'
import {daily, useMarketHistory} from './useMarketHistory'

const STONE: MarketDetailView = {
    item: {material: 'minecraft:stone', name: 'Stone', amount: 1, icon: 'stone'},
    days: 30,
    to: '2026-10-23',
    history: [
        {day: '2026-10-20', orders: 4, items: 40, avg: 10},
        {day: '2026-10-21', orders: 1, items: 10},
        {day: '2026-10-22', orders: 3, items: 30, avg: 11},
    ],
}

/** Loading a material's price history. */
describe('the price history', () => {
    it('places each day by its date and leaves gaps for days without a price', () => {
        const series = daily({...STONE, days: 5})

        expect(series).toEqual([null, 10, null, 11, null])
    })

    it('counts the priced days and loads again for another period', async () => {
        const api = fakeApi()
        const read = vi.fn().mockResolvedValue(STONE)
        api.read = read as unknown as GatewayApi['read']
        const history = useMarketHistory(ref('minecraft:stone'), api)
        await flushPromises()

        expect(history.priced.value).toBe(2)
        expect(history.series.value).toHaveLength(30)
        expect(read).toHaveBeenLastCalledWith('market.item', {material: 'minecraft:stone', days: 30})

        history.range.value = 90
        await flushPromises()
        expect(read).toHaveBeenLastCalledWith('market.item', {material: 'minecraft:stone', days: 90})
    })

    it('drops the old history at once when another material opens', async () => {
        const api = fakeApi()
        api.read = vi.fn()
            .mockResolvedValueOnce(STONE)
            .mockReturnValueOnce(new Promise(() => {})) as unknown as GatewayApi['read']
        const material = ref<string | null>('minecraft:stone')
        const history = useMarketHistory(material, api)
        await flushPromises()

        material.value = 'minecraft:dirt'
        await flushPromises()

        expect(history.detail.value).toBeNull()
        expect(history.status.value).toBe('loading')
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
