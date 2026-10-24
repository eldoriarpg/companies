/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {ActionRefused, type GatewayApi} from '~/api/gateway'
import {catalogue, knownItems, material} from '~/test/catalogue'
import {fakeApi} from '~/test/fakes'
import {useOrderBuilder} from './useOrderBuilder'

beforeEach(() => {
    vi.useFakeTimers()
})

afterEach(() => {
    vi.useRealTimers()
})

/**
 * A gateway answering the catalogue reads and quoting with the given answer.
 *
 * @param quote what a quote answers or throws
 * @returns the gateway and its read mock
 */
function gateway(quote: () => unknown) {
    const api = fakeApi()
    const read = vi.fn(async (name: string) => {
        if (name === 'orders.create.catalogue') return catalogue()
        if (name === 'orders.create.items') return knownItems()
        return quote()
    })
    api.read = read as unknown as GatewayApi['read']
    return {api, read}
}

/** Building an order: loading what it is built from, quoting as it changes, placing it. */
describe('the order builder', () => {
    it('loads the catalogue and the known items', async () => {
        const {api} = gateway(() => ({}))
        const builder = useOrderBuilder(api)

        await builder.load()

        expect(builder.status.value).toBe('ready')
        expect(builder.catalogue.value?.materials).toHaveLength(5)
        expect(builder.known.value?.saved[0]?.ref).toBe('saved:1')
    })

    it('quotes once the order is complete, after the typing settles', async () => {
        const {api, read} = gateway(() => ({price: 128, fee: 6.4, total: 134.4}))
        const builder = useOrderBuilder(api)
        builder.name.value = 'Walls'
        builder.addLine({kind: 'material', material: material('stone', 'Stone')}, 2)
        builder.lines.value[0]!.amount = 64

        await vi.advanceTimersByTimeAsync(500)

        expect(read).toHaveBeenCalledWith('orders.create.quote', {name: 'Walls', lines: [{item: 'minecraft:stone', amount: 64, unitPrice: 2}]})
        expect(builder.quote.value).toEqual({status: 'quoted', quote: {price: 128, fee: 6.4, total: 134.4}})
    })

    it('says why the plugin would refuse it', async () => {
        const {api} = gateway(() => {
            throw new ActionRefused('order.create.error.itemLimit', 'An order may hold at most 512 items.')
        })
        const builder = useOrderBuilder(api)
        builder.name.value = 'Walls'
        builder.addLine({kind: 'material', material: material('stone', 'Stone')}, 2)

        await vi.advanceTimersByTimeAsync(500)

        expect(builder.quote.value).toEqual({status: 'refused', text: 'An order may hold at most 512 items.'})
    })

    it('stays incomplete while a line has no price', async () => {
        const {api, read} = gateway(() => ({price: 1, fee: 0, total: 1}))
        const builder = useOrderBuilder(api)
        builder.name.value = 'Walls'
        builder.addLine({kind: 'material', material: material('stone', 'Stone')})

        await vi.advanceTimersByTimeAsync(500)

        expect(read).not.toHaveBeenCalled()
        expect(builder.quote.value).toEqual({status: 'incomplete'})
        expect(builder.complete.value).toBe(false)
    })

    it('places the order and names it', async () => {
        const {api} = gateway(() => ({price: 2, fee: 0, total: 2}))
        api.act = vi.fn().mockResolvedValue({order: 7, price: 2, fee: 0, total: 2}) as unknown as GatewayApi['act']
        const builder = useOrderBuilder(api)
        builder.name.value = 'Walls'
        builder.addLine({kind: 'material', material: material('stone', 'Stone')}, 2)

        expect(await builder.submit()).toBe(7)
        expect(api.act).toHaveBeenCalledWith('orders.create', {name: 'Walls', lines: [{item: 'minecraft:stone', amount: 1, unitPrice: 2}]})
    })

    it('removes a line', () => {
        const {api} = gateway(() => ({}))
        const builder = useOrderBuilder(api)
        builder.addLine({kind: 'material', material: material('stone', 'Stone')}, 2)
        builder.addLine({kind: 'material', material: material('stone', 'Stone')}, 3)

        builder.removeLine(builder.lines.value[0]!.id)

        expect(builder.lines.value.map(line => line.unitPrice)).toEqual([3])
    })
})
