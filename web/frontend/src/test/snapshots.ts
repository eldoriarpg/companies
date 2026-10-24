/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {ClaimedOrderView, MemberView, Patch, Snapshot} from '~/api/generated/protocol'

/** The node the test snapshot was built on. */
export const NODE = '4f6c1f0e-2b7a-4c39-9a51-0d6f2c7e9b10'

/** Steve, the player of the test snapshot. */
export const STEVE = 'a3b1c2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d'

/** Alex, the company's owner. */
export const ALEX = '0b1c2d3e-4f5a-4b6c-9d7e-8f9a0b1c2d3e'

/**
 * A member of the test company.
 *
 * @param uuid the member's id
 * @param name the member's name
 * @param items the items delivered
 * @returns the member
 */
export function member(uuid: string, name: string, items: number): MemberView {
    return {uuid, name, permissions: uuid === ALEX ? ['OWNER'] : ['MANAGE_ORDERS'], stats: {orders: 3, items, earned: items * 2}}
}

/**
 * An order the test company processes.
 *
 * @param id the order's id
 * @param delivered the diamonds delivered of 64
 * @returns the order
 */
export function claimed(id: number, delivered: number): ClaimedOrderView {
    return {
        id,
        name: 'Diamonds for a beacon',
        state: 'CLAIMED',
        lines: [{item: {material: 'minecraft:diamond', name: 'Diamond', amount: 64, icon: 'minecraft:diamond'}, delivered, price: 640}],
        claimed: '2026-10-24T10:00:00Z',
        deadline: '2026-10-25T10:00:00Z',
    }
}

/**
 * The snapshot of Steve in Diamond Diggers, as the protocol's recorded message has it.
 *
 * @returns a fresh copy
 */
export function snapshot(): Snapshot {
    return {
        type: 'snapshot',
        v: 1,
        session: 's-7f3a9c',
        node: NODE,
        seq: 41,
        player: {uuid: STEVE, name: 'Steve', company: 7, permissions: ['MANAGE_ORDERS', 'TREASURY'], invites: [], unreadInbox: 3},
        company: {
            id: 7,
            name: 'Diamond Diggers',
            tag: 'DD',
            joinMode: 'APPLICATIONS',
            founded: '2025-03-14T18:00:00Z',
            level: {level: 3, name: 'Guild', progress: 0.4},
            members: [member(STEVE, 'Steve', 840), member(ALEX, 'Alex', 2210)],
            treasury: {balance: 2500},
            orders: [claimed(101, 20)],
            alerts: [],
        },
        orders: [],
        rankings: {ranking: 'orders', range: 'all', page: 1, pages: 1, entries: []},
    }
}

/**
 * A patch of the test node.
 *
 * @param seq its sequence number
 * @param change the path, operation, key and value
 * @returns the patch
 */
export function patch(seq: number, change: Pick<Patch, 'path' | 'op'> & Partial<Pick<Patch, 'key' | 'value' | 'node'>>): Patch {
    return {type: 'patch', v: 1, sessions: ['s-7f3a9c'], node: NODE, seq, ...change}
}
