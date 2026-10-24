/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import type {OrderLineView} from '~/api/generated/protocol'

/** What an order adds up to over its lines. */
export interface OrderTotals {
    /** Items asked for. */
    amount: number
    /** Items delivered so far. */
    delivered: number
    /** What the order pays. */
    payout: number
    /** What it pays per item. */
    perItem: number
    /** How much is delivered, 0 to 100. */
    progress: number
}

/**
 * The totals of an order's lines.
 *
 * @param lines the lines
 * @returns their totals
 */
export function totalsOf(lines: readonly OrderLineView[]): OrderTotals {
    const amount = lines.reduce((sum, line) => sum + line.item.amount, 0)
    const delivered = lines.reduce((sum, line) => sum + Math.min(line.delivered, line.item.amount), 0)
    const payout = lines.reduce((sum, line) => sum + line.price, 0)
    return {
        amount,
        delivered,
        payout,
        perItem: amount === 0 ? 0 : payout / amount,
        progress: amount === 0 ? 0 : Math.round(delivered / amount * 100),
    }
}

/**
 * How far an order's price per item lies above or below the market's average of its material, in percent. Only an
 * order of one material compares; mixed orders and materials without an average have no comparison.
 *
 * @param lines the order's lines
 * @param averages the market's average price per item, by material key
 * @returns the difference in percent, or null where there is nothing to compare
 */
export function versusAverage(lines: readonly OrderLineView[], averages: ReadonlyMap<string, number>): number | null {
    const materials = new Set(lines.map(line => line.item.material))
    if (materials.size !== 1) return null
    const average = averages.get([...materials][0] as string)
    if (!average) return null
    return (totalsOf(lines).perItem / average - 1) * 100
}

/**
 * The items of an order in one line of text, such as "Sea Lantern ×48 · Prismarine ×512".
 *
 * @param lines the order's lines
 * @param count writes a count in the reader's format
 * @returns the text
 */
export function itemsText(lines: readonly OrderLineView[], count: (value: number) => string): string {
    return lines.map(line => `${line.item.name} ×${count(line.item.amount)}`).join(' · ')
}
