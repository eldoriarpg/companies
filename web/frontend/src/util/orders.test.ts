/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {OrderLineView} from '~/api/generated/protocol'
import {itemsText, totalsOf, versusAverage} from './orders'

/**
 * A line of an order.
 *
 * @param material the material key
 * @param amount the items asked for
 * @param delivered the items delivered
 * @param price what the line pays
 * @returns the line
 */
function line(material: string, amount: number, delivered: number, price: number): OrderLineView {
    return {item: {material, name: material.replace('minecraft:', ''), amount, icon: material}, delivered, price}
}

/** What an order adds up to. */
describe('orders', () => {
    it('adds up its lines', () => {
        expect(totalsOf([line('minecraft:diamond', 64, 20, 640), line('minecraft:stone', 36, 50, 160)]))
            .toEqual({amount: 100, delivered: 56, payout: 800, perItem: 8, progress: 56})
    })

    it('has no progress without items', () => {
        expect(totalsOf([]).progress).toBe(0)
    })

    it('compares the price per item with the market average', () => {
        const averages = new Map([['minecraft:diamond', 8]])
        expect(versusAverage([line('minecraft:diamond', 64, 0, 640)], averages)).toBeCloseTo(25)
        expect(versusAverage([line('minecraft:stone', 64, 0, 640)], averages)).toBeNull()
        expect(versusAverage([line('minecraft:diamond', 1, 0, 1), line('minecraft:stone', 1, 0, 1)], averages)).toBeNull()
    })

    it('writes its items in one line', () => {
        expect(itemsText([line('minecraft:diamond', 64, 0, 1), line('minecraft:stone', 512, 0, 1)], String)).toBe('diamond ×64 · stone ×512')
    })
})
