/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {CatalogueMaterial, CriteriaArgs, ItemDefinition, KnownItem, OrderCatalogue, OrderCreateArgs, OrderLineArgs, RepetitionArgs} from '~/api/gateway'
import type {CriteriaView, ItemView} from '~/api/generated/protocol'

/**
 * Where a line's item comes from: a plain material, a saved or earlier item, an item defined in the browser, or
 * criteria any of a set of items meets. A criteria line keeps the item that stands for it, named after the set.
 */
export type LineSource =
    | {kind: 'material', material: CatalogueMaterial}
    | {kind: 'known', known: KnownItem}
    | {kind: 'defined', material: CatalogueMaterial, definition: ItemDefinition}
    | {kind: 'criteria', criteria: CriteriaArgs, item: ItemView}

/** One line of the order being built. Amount and unit price are empty until the player types a readable one. */
export interface DraftLine {
    id: number
    source: LineSource
    amount?: number
    unitPrice?: number
}

/** How the order being built repeats, as the form holds it. */
export interface DraftRepetition {
    enabled: boolean
    interval: RepetitionArgs['interval']
    end: 'times' | 'until'
    times?: number
    until: string
}

/**
 * The item a line stands for.
 *
 * @param source where the item comes from
 * @returns the item
 */
export function itemOf(source: LineSource): ItemView {
    if (source.kind === 'known') return source.known.item
    if (source.kind === 'criteria') return source.item
    return source.material.item
}

const ROMAN = ['', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X']

/**
 * A level as the game writes it.
 *
 * @param level the level
 * @returns the Roman numeral, the number itself above ten
 */
export function roman(level: number): string {
    return ROMAN[level] ?? String(level)
}

/**
 * The name of an entry of a criteria set: the catalogue's name of a material or tag, else the key.
 *
 * @param entry a material key or a tag such as `#minecraft:logs`
 * @param catalogue the names
 * @returns the name
 */
export function entryName(entry: string, catalogue: OrderCatalogue | null): string {
    if (entry.startsWith('#')) return catalogue?.tags?.find(tag => tag.key === entry)?.name ?? entry
    return catalogue?.materials.find(material => material.item.material === entry)?.item.name ?? entry
}

/** Writes the enchantment count of criteria, such as "Enchantments: 2 or more". */
export type CountText = (count: number) => string

/**
 * The conditions of criteria as short lines: the enchantment count and each required enchantment with its level.
 *
 * @param criteria the criteria
 * @param catalogue the enchantment names
 * @param countText writes the count
 * @returns the lines, empty without conditions
 */
export function conditionsOf(criteria: CriteriaArgs, catalogue: OrderCatalogue | null, countText: CountText): string[] {
    const required = Object.entries(criteria.enchantments ?? {}).map(([key, level]) => ({
        name: catalogue?.enchantments.find(entry => entry.key === key)?.name ?? key,
        minLevel: level,
    }))
    return conditionLines(criteria.minEnchantments ?? 0, required, countText)
}

/**
 * The conditions of a criteria line as the plugin sent them, as short lines.
 *
 * @param criteria the criteria of the line
 * @param countText writes the count
 * @returns the lines, empty without conditions
 */
export function viewConditions(criteria: CriteriaView, countText: CountText): string[] {
    return conditionLines(criteria.minEnchantments, criteria.enchantments, countText)
}

/**
 * Writes conditions: the count when it asks for more than the required enchantments, then each of them.
 *
 * @param count the fewest enchantments
 * @param required the required enchantments with their names
 * @param countText writes the count
 * @returns the lines
 */
function conditionLines(count: number, required: {name: string, minLevel: number}[], countText: CountText): string[] {
    const lines: string[] = []
    if (count > required.length) lines.push(countText(count))
    for (const {name, minLevel} of required) lines.push(minLevel > 1 ? `${name} ${roman(minLevel)}+` : name)
    return lines
}

/**
 * The line source of criteria: the item standing for them is the first material of the set, named after the set.
 *
 * @param criteria the criteria
 * @param catalogue the materials, tags and names
 * @returns the source, or null when the set names nothing the catalogue knows
 */
export function criteriaSource(criteria: CriteriaArgs, catalogue: OrderCatalogue): LineSource | null {
    const first = criteria.items
        .flatMap(entry => (entry.startsWith('#') ? catalogue.tags?.find(tag => tag.key === entry)?.materials ?? [] : [entry]))
        .map(key => catalogue.materials.find(material => material.item.material === key))
        .find(material => material !== undefined)
    if (!first) return null
    const name = criteria.items.map(entry => entryName(entry, catalogue)).join(' / ')
    return {kind: 'criteria', criteria, item: {...first.item, name}}
}

/**
 * What a line's item carries, as short lines: the details the plugin named for a known item, the chosen properties
 * for a defined one. An enchantment with a single level is named without it, as the plugin names it.
 *
 * @param source where the item comes from
 * @param catalogue the names of the properties
 * @param countText writes the enchantment count of criteria
 * @returns the lines, empty for a plain material
 */
export function detailsOf(source: LineSource, catalogue: OrderCatalogue | null, countText: CountText): string[] {
    if (source.kind === 'known') return source.known.details
    if (source.kind === 'material') return []
    if (source.kind === 'criteria') return conditionsOf(source.criteria, catalogue, countText)
    const {definition} = source
    const named = (entries: {key: string, name: string}[] | undefined, key: string) =>
        entries?.find(entry => entry.key === key)?.name ?? key
    const lines = Object.entries(definition.enchantments ?? {}).map(([key, level]) => {
        const name = named(catalogue?.enchantments, key)
        return catalogue?.enchantments.find(entry => entry.key === key)?.max === 1 ? name : `${name} ${level}`
    })
    if (definition.potion) lines.push(named(catalogue?.potions, definition.potion))
    if (definition.dye) lines.push(named(catalogue?.dyes, definition.dye))
    if (definition.trim) {
        lines.push(`${named(catalogue?.trimPatterns, definition.trim.pattern)} (${named(catalogue?.trimMaterials, definition.trim.material)})`)
    }
    return lines
}

/**
 * Whether a material can be given anything, and so offers the item editor.
 *
 * @param material the material
 * @returns true when it takes enchantments, a potion type, a dye or a trim
 */
export function definable(material: CatalogueMaterial): boolean {
    return material.enchantments.length > 0 || material.potion || material.dye || material.trim
}

/**
 * A definition with nothing chosen left out, so a material given nothing is ordered as the plain material.
 *
 * @param definition the definition as the editor holds it
 * @returns the definition, or null when nothing was chosen
 */
export function trimmed(definition: ItemDefinition): ItemDefinition | null {
    const enchantments = Object.fromEntries(Object.entries(definition.enchantments ?? {}).filter(([, level]) => level > 0))
    const result: ItemDefinition = {material: definition.material}
    if (Object.keys(enchantments).length > 0) result.enchantments = enchantments
    if (definition.potion) result.potion = definition.potion
    if (definition.dye) result.dye = definition.dye
    if (definition.trim?.pattern && definition.trim.material) result.trim = definition.trim
    return Object.keys(result).length > 1 ? result : null
}

/**
 * The enchantments a chosen set excludes: those that conflict with one already chosen.
 *
 * @param chosen the chosen enchantment keys
 * @param catalogue the enchantments with their conflicts
 * @returns the excluded keys
 */
export function excluded(chosen: string[], catalogue: OrderCatalogue): Set<string> {
    const result = new Set<string>()
    for (const key of chosen) {
        for (const conflict of catalogue.enchantments.find(entry => entry.key === key)?.conflicts ?? []) result.add(conflict)
    }
    return result
}

/**
 * A line as `orders.create` takes it.
 *
 * @param line the line
 * @returns the arguments, or null while its amount or unit price is missing
 */
export function lineArgs(line: DraftLine): OrderLineArgs | null {
    if (line.amount === undefined || line.unitPrice === undefined) return null
    const {source} = line
    if (source.kind === 'known') return {item: source.known.ref, amount: line.amount, unitPrice: line.unitPrice}
    if (source.kind === 'defined') return {define: source.definition, amount: line.amount, unitPrice: line.unitPrice}
    if (source.kind === 'criteria') return {criteria: source.criteria, amount: line.amount, unitPrice: line.unitPrice}
    return {item: source.material.item.material, amount: line.amount, unitPrice: line.unitPrice}
}

/**
 * The repetition as `orders.create` takes it.
 *
 * @param repetition the repetition as the form holds it
 * @returns the arguments, none when the order does not repeat, null while its end is missing
 */
export function repetitionArgs(repetition: DraftRepetition): RepetitionArgs | undefined | null {
    if (!repetition.enabled) return undefined
    if (repetition.end === 'times') {
        return repetition.times === undefined ? null : {interval: repetition.interval, times: repetition.times}
    }
    return repetition.until === '' ? null : {interval: repetition.interval, until: repetition.until}
}

/**
 * The order as `orders.create` takes it.
 *
 * @param name the order's name
 * @param lines its lines
 * @param repetition how it repeats
 * @returns the arguments, or null while anything is missing
 */
export function orderArgs(name: string, lines: DraftLine[], repetition: DraftRepetition): OrderCreateArgs | null {
    const trimmedName = name.trim()
    if (trimmedName === '' || lines.length === 0) return null
    const args = lines.map(lineArgs)
    if (args.some(line => line === null)) return null
    const repeat = repetitionArgs(repetition)
    if (repeat === null) return null
    return {name: trimmedName, lines: args as OrderLineArgs[], ...(repeat ? {repetition: repeat} : {})}
}
