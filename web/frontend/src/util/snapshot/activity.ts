/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {ClaimedOrderView, CompanyView, Patch} from '~/api/generated/protocol'

/** What happened in the company. */
export type ActivityKind =
    | 'delivered' | 'accepted' | 'done' | 'dropped' | 'joined' | 'left' | 'level' | 'deposit' | 'withdrawal'
    | 'companyJoined' | 'companyLeft'

/**
 * One line of the company's live feed. Inside a company anonymity ends, so a delivery names the member who made it;
 * the owner of an order is never part of a line.
 */
export interface ActivityEntry {
    id: string
    kind: ActivityKind
    time: string
    /** The member it was, where the patches tell. */
    actor?: string
    /** The order it was about. */
    order?: string
    /** The item delivered. */
    item?: string
    /** Items delivered or money moved. */
    amount?: number
    /** The level reached. */
    level?: string
    /** The company the player joined or left. */
    company?: string
}

/** How long a delivery waits for its other half: the member's statistics and the order's progress come apart. */
const PAIRING_MILLIS = 10_000

/** How many lines the feed keeps. */
export const FEED_LENGTH = 50

/**
 * The lines one patch adds to the feed, read from the company before and after it.
 *
 * <p>The protocol has no feed of its own, so the feed is what the patches show: a member whose delivered items rose
 * delivered them, an order whose lines rose was delivered to, a new order was accepted, a gone one is done or dropped,
 * a higher level was reached, a member came or went, the treasury moved. The plugin sends a changed level, and other
 * changes of the profile, as the whole `company` replaced; such a patch, and a whole list replaced, is compared part
 * by part. A whole `company` that is another one, or that comes or goes, means the player joined, switched or left
 * a company: that is one line, and nothing of the two companies is compared.
 *
 * @param before the company before the patch
 * @param after the company after it
 * @param patch the patch
 * @param time when it arrived
 * @returns the new lines, without ids
 */
export function activityOf(before: CompanyView | undefined, after: CompanyView | undefined, patch: Patch, time: string): Omit<ActivityEntry, 'id'>[] {
    if (patch.path === 'company' && (before?.id !== after?.id)) return companyChange(after ?? undefined, before ?? undefined, time)
    if (!before || !after) return []
    switch (patch.path) {
        case 'company':
            return [
                ...levelActivity(before, after, time),
                ...treasuryActivity(before, after, time),
                ...memberKeys(before, after).flatMap(key => memberActivity(before, after, key, time)),
                ...orderKeys(before, after).flatMap(key => orderActivity(before, after, key, time)),
            ]
        case 'company.members':
            return (patch.key === undefined ? memberKeys(before, after) : [patch.key]).flatMap(key => memberActivity(before, after, key, time))
        case 'company.orders':
            return (patch.key === undefined ? orderKeys(before, after) : [patch.key]).flatMap(key => orderActivity(before, after, key, time))
        case 'company.level':
            return levelActivity(before, after, time)
        case 'company.treasury':
            return treasuryActivity(before, after, time)
        default:
            return []
    }
}

/**
 * The line of the player joining a company, switching to another one, or leaving theirs.
 *
 * @param joined the company the player is in now, none after leaving
 * @param left the company the player was in, none before joining
 * @param time when it arrived
 * @returns the line
 */
function companyChange(joined: CompanyView | undefined, left: CompanyView | undefined, time: string): Omit<ActivityEntry, 'id'>[] {
    if (joined) return [{kind: 'companyJoined', time, company: joined.name}]
    return left ? [{kind: 'companyLeft', time, company: left.name}] : []
}

/**
 * The members of the company before and after, by their key.
 *
 * @param before the company before
 * @param after the company after
 * @returns the keys of both
 */
function memberKeys(before: CompanyView, after: CompanyView): string[] {
    return [...new Set([...before.members, ...after.members].map(member => member.uuid))]
}

/**
 * The orders of the company before and after, by their key.
 *
 * @param before the company before
 * @param after the company after
 * @returns the keys of both
 */
function orderKeys(before: CompanyView, after: CompanyView): string[] {
    return [...new Set([...before.orders, ...after.orders].map(order => String(order.id)))]
}

/**
 * The line of a higher level.
 *
 * @param before the company before
 * @param after the company after
 * @param time when it arrived
 * @returns the line
 */
function levelActivity(before: CompanyView, after: CompanyView, time: string): Omit<ActivityEntry, 'id'>[] {
    return after.level.level > before.level.level ? [{kind: 'level', time, level: after.level.name}] : []
}

/**
 * The lines of one member: joined, left, or delivered items.
 *
 * @param before the company before
 * @param after the company after
 * @param key the member's id
 * @param time when it arrived
 * @returns the lines
 */
function memberActivity(before: CompanyView, after: CompanyView, key: string, time: string): Omit<ActivityEntry, 'id'>[] {
    const old = before.members.find(entry => entry.uuid === key)
    const now = after.members.find(entry => entry.uuid === key)
    if (!old && now) return [{kind: 'joined', time, actor: now.name}]
    if (old && !now) return [{kind: 'left', time, actor: old.name}]
    const delivered = (now?.stats.items ?? 0) - (old?.stats.items ?? 0)
    return delivered > 0 ? [{kind: 'delivered', time, actor: now?.name, amount: delivered}] : []
}

/**
 * The lines of one order: accepted, delivered to, done or dropped.
 *
 * @param before the company before
 * @param after the company after
 * @param key the order's id
 * @param time when it arrived
 * @returns the lines
 */
function orderActivity(before: CompanyView, after: CompanyView, key: string, time: string): Omit<ActivityEntry, 'id'>[] {
    const old = before.orders.find(order => String(order.id) === key)
    const now = after.orders.find(order => String(order.id) === key)
    if (!old && now) return [{kind: 'accepted', time, order: now.name}]
    if (old && !now) return [{kind: complete(old) ? 'done' : 'dropped', time, order: old.name}]
    if (!old || !now) return []
    return now.lines.flatMap((line, index) => {
        const amount = line.delivered - (old.lines[index]?.delivered ?? 0)
        return amount > 0 ? [{kind: 'delivered' as const, time, order: now.name, item: line.item.name, amount}] : []
    })
}

/**
 * The line of a treasury patch: money in or out.
 *
 * @param before the company before
 * @param after the company after
 * @param time when it arrived
 * @returns the line
 */
function treasuryActivity(before: CompanyView, after: CompanyView, time: string): Omit<ActivityEntry, 'id'>[] {
    const delta = (after.treasury?.balance ?? 0) - (before.treasury?.balance ?? 0)
    if (delta === 0 || !before.treasury || !after.treasury) return []
    return [{kind: delta > 0 ? 'deposit' : 'withdrawal', time, amount: Math.abs(delta)}]
}

/**
 * Whether every line of an order is delivered.
 *
 * @param order the order
 * @returns true when nothing is missing
 */
function complete(order: ClaimedOrderView): boolean {
    return order.lines.every(line => line.delivered >= line.item.amount)
}

/**
 * The feed with a new line on top. A delivery that arrives as its two halves, the member's statistics and the order's
 * progress, becomes one line naming both the member and the order.
 *
 * @param feed the feed, newest first
 * @param entry the new line
 * @returns the new feed
 */
export function addActivity(feed: readonly ActivityEntry[], entry: ActivityEntry): ActivityEntry[] {
    const half = entry.kind === 'delivered' ? feed.find(other => pairs(other, entry)) : undefined
    if (!half) return [entry, ...feed].slice(0, FEED_LENGTH)
    const merged: ActivityEntry = {...half, actor: half.actor ?? entry.actor, order: half.order ?? entry.order, item: half.item ?? entry.item}
    return feed.map(other => (other === half ? merged : other))
}

/**
 * Whether two delivery lines are the halves of one delivery: the same amount, close in time, one with the member and
 * the other with the order.
 *
 * @param other a line of the feed
 * @param entry the new line
 * @returns true for two halves
 */
function pairs(other: ActivityEntry, entry: ActivityEntry): boolean {
    if (other.kind !== 'delivered' || other.amount !== entry.amount) return false
    if (Math.abs(new Date(entry.time).getTime() - new Date(other.time).getTime()) > PAIRING_MILLIS) return false
    return (other.actor === undefined) !== (entry.actor === undefined) && (other.order === undefined) !== (entry.order === undefined)
}
