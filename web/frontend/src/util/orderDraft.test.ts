/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {catalogue, knownItems, material} from '~/test/catalogue'
import {type DraftRepetition, definable, detailsOf, excluded, orderArgs, trimmed} from './orderDraft'

const ONCE: DraftRepetition = {enabled: false, interval: 'weekly', end: 'times', times: undefined, until: ''}

/**
 * Turning the order being built into what the plugin takes.
 *
 * @vitest-environment node
 */
describe('an order draft', () => {
    it('is incomplete without a name, a line, an amount, a price or the end of its repetition', () => {
        const stone = {id: 1, source: {kind: 'material' as const, material: material('stone', 'Stone')}, amount: 64, unitPrice: 2}
        expect(orderArgs(' ', [stone], ONCE)).toBeNull()
        expect(orderArgs('Walls', [], ONCE)).toBeNull()
        expect(orderArgs('Walls', [{...stone, unitPrice: undefined}], ONCE)).toBeNull()
        expect(orderArgs('Walls', [stone], {...ONCE, enabled: true})).toBeNull()
        expect(orderArgs('Walls', [stone], {...ONCE, enabled: true, end: 'until'})).toBeNull()
    })

    it('names each line by material, known ref or definition', () => {
        const blade = knownItems().saved[0]!
        const sword = material('diamond_sword', 'Diamond Sword', {enchantments: ['minecraft:sharpness']})
        const args = orderArgs(' Walls ', [
            {id: 1, source: {kind: 'material', material: material('stone', 'Stone')}, amount: 64, unitPrice: 2},
            {id: 2, source: {kind: 'known', known: blade}, amount: 1, unitPrice: 300},
            {id: 3, source: {kind: 'defined', material: sword, definition: {material: 'minecraft:diamond_sword', enchantments: {'minecraft:sharpness': 5}}}, amount: 1, unitPrice: 500},
        ], {...ONCE, enabled: true, interval: 'daily', times: 3})

        expect(args).toEqual({
            name: 'Walls',
            lines: [
                {item: 'minecraft:stone', amount: 64, unitPrice: 2},
                {item: 'saved:1', amount: 1, unitPrice: 300},
                {define: {material: 'minecraft:diamond_sword', enchantments: {'minecraft:sharpness': 5}}, amount: 1, unitPrice: 500},
            ],
            repetition: {interval: 'daily', times: 3},
        })
    })

    it('leaves out what the editor did not choose', () => {
        expect(trimmed({material: 'minecraft:diamond_sword', enchantments: {'minecraft:sharpness': 0}})).toBeNull()
        expect(trimmed({material: 'minecraft:iron_helmet', trim: {pattern: 'minecraft:coast', material: ''}})).toBeNull()
        expect(trimmed({material: 'minecraft:potion', potion: 'minecraft:swiftness'})).toEqual({material: 'minecraft:potion', potion: 'minecraft:swiftness'})
    })

    it('describes a defined item by the catalogue names', () => {
        const tunic = material('leather_chestplate', 'Leather Tunic', {dye: true, trim: true})
        expect(detailsOf({kind: 'defined', material: tunic, definition: {
            material: 'minecraft:leather_chestplate', dye: 'red', trim: {pattern: 'minecraft:coast', material: 'minecraft:gold'},
        }}, catalogue())).toEqual(['Red', 'Coast (Gold)'])
    })

    it('names an enchantment of a single level without it', () => {
        const book = material('enchanted_book', 'Enchanted Book', {enchantments: ['minecraft:mending']})
        const withMending = {...catalogue(), enchantments: [{key: 'minecraft:mending', name: 'Mending', max: 1, conflicts: []}]}
        expect(detailsOf({kind: 'defined', material: book, definition: {material: 'minecraft:enchanted_book', enchantments: {'minecraft:mending': 1}}}, withMending)).toEqual(['Mending'])
    })

    it('excludes what conflicts with a chosen enchantment', () => {
        expect([...excluded(['minecraft:sharpness'], catalogue())]).toEqual(['minecraft:smite'])
    })

    it('offers the editor only for a material that can be given something', () => {
        expect(definable(material('stone', 'Stone'))).toBe(false)
        expect(definable(material('potion', 'Potion', {potion: true}))).toBe(true)
    })
})
