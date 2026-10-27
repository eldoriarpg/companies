/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {CatalogueMaterial, KnownItems, OrderCatalogue} from '~/api/gateway'

/**
 * A material of the catalogue.
 *
 * @param key its key without namespace
 * @param name its name
 * @param gives what survival can give it
 * @returns the material
 */
export function material(key: string, name: string, gives: Partial<Omit<CatalogueMaterial, 'item'>> = {}): CatalogueMaterial {
    return {
        item: {material: `minecraft:${key}`, name, amount: 1, icon: key},
        enchantments: [],
        potion: false,
        dye: false,
        trim: false,
        ...gives,
    }
}

/**
 * A small catalogue: stone, a sword that takes sharpness or smite, a potion, a leather tunic and an iron helmet.
 *
 * @returns the catalogue
 */
export function catalogue(): OrderCatalogue {
    return {
        materials: [
            material('stone', 'Stone'),
            material('diamond_sword', 'Diamond Sword', {enchantments: ['minecraft:sharpness', 'minecraft:smite']}),
            material('potion', 'Potion', {potion: true}),
            material('leather_chestplate', 'Leather Tunic', {dye: true, trim: true}),
            material('iron_helmet', 'Iron Helmet', {trim: true}),
        ],
        enchantments: [
            {key: 'minecraft:sharpness', name: 'Sharpness', max: 5, conflicts: ['minecraft:smite']},
            {key: 'minecraft:smite', name: 'Smite', max: 5, conflicts: ['minecraft:sharpness']},
        ],
        potions: [{key: 'minecraft:swiftness', name: 'Swiftness'}],
        dyes: [{key: 'red', name: 'Red', color: '#b02e26'}],
        trimPatterns: [{key: 'minecraft:coast', name: 'Coast'}],
        trimMaterials: [{key: 'minecraft:gold', name: 'Gold'}],
        tags: [{key: '#minecraft:swords', name: '#Swords', materials: ['minecraft:diamond_sword']}],
    }
}

/**
 * The player's known items: one saved blade.
 *
 * @returns the items
 */
export function knownItems(): KnownItems {
    return {
        saved: [{ref: 'saved:1', label: 'blade', item: {material: 'minecraft:diamond_sword', name: 'Diamond Sword', amount: 1, icon: 'diamond_sword'}, details: ['Sharpness 3']}],
        earlier: [],
    }
}
