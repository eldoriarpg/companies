/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */

/** The node the mock server's snapshot and most patches come from. */
export const NODE = '4f6c1f0e-2b7a-4c39-9a51-0d6f2c7e9b10'

/** A second node of the mock server, whose numbers run apart from the first's. */
export const OTHER_NODE = '7d1e2f3a-4b5c-4d6e-8f70-819203a4b5c6'

/** The code every waiting session of the mock takes. */
export const CODE = 'KXT4PR'

const SUPPORTED = {status: 'SUPPORTED'}

/** The version state of a discontinued server. */
export const DISCONTINUED = {
    status: 'DISCONTINUED',
    since: '2026-09-01',
    updateTo: '2.4.0',
    message: 'Companies 2.0 uses an order format the dashboard no longer reads.',
}

/**
 * The waiting sessions of the mock by short id, each a story of its own: the version state of the server, and what
 * the login, the server, the snapshot and the stream do.
 */
export const SESSIONS = {
    MOSS01: {version: SUPPORTED, stream: 'deliver'},
    CALM01: {version: SUPPORTED},
    NOMK01: {version: SUPPORTED, features: ['orders.browse', 'inbox', 'rankings']},
    DEPR01: {
        version: {
            status: 'DEPRECATED',
            discontinuedAt: '2027-04-30',
            updateTo: '2.4.0',
            message: 'Companies 2.1 uses an old order format the dashboard stops reading.',
        },
    },
    GONE01: {version: DISCONTINUED},
    DISC01: {version: SUPPORTED, login: 'discontinued'},
    LOCK01: {version: SUPPORTED, login: 'locked'},
    TURN01: {version: SUPPORTED, turnstile: 'mock-site-key'},
    OFFL01: {version: SUPPORTED, offline: true},
    DOWN01: {version: SUPPORTED, snapshot: 503},
    KICK01: {version: SUPPORTED, stream: 'kick'},
    DCON01: {version: SUPPORTED, stream: 'discontinue'},
}

/** The features of the mock server. */
export const FEATURES = ['orders.browse', 'orders.accept', 'orders.own', 'company.members', 'company.treasury', 'inbox', 'rankings', 'market']

const MEMBERS = [
    ['Wren', ['OWNER'], 4812], ['Kestrel_', ['MANAGE_ORDERS', 'TREASURY'], 3240], ['mossycobble', [], 1155],
    ['Tidewalker', [], 402], ['Brambleby', ['INVITE', 'KICK'], 380], ['quartz_owl', [], 351], ['Ferrous', [], 290],
    ['LanternLad', [], 244], ['Heathrow', ['EDIT_PROFILE'], 198], ['pebblepush', [], 120], ['Morrow', [], 64],
    ['ash_and_oak', [], 12], ['Vireo', [], 0], ['Sundial', [], 0],
]

/**
 * A member of the mock company.
 *
 * @param index its place in the list
 * @param items the items it delivered
 * @returns the member
 */
export function member(index, items = MEMBERS[index][2]) {
    const [name, permissions] = MEMBERS[index]
    return {
        uuid: `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`,
        name,
        permissions,
        stats: {orders: Math.round(items / 100), items, earned: items * 1.5},
    }
}

/**
 * An item of the mock.
 *
 * @param name the item's name
 * @param amount how many
 * @returns the item
 */
function item(name, amount) {
    const material = `minecraft:${name.toLowerCase().replaceAll(' ', '_')}`
    return {material, name, amount, icon: material}
}

/**
 * A claimed order of the mock company.
 *
 * @param id the order's id
 * @param name its name
 * @param lines its lines as name, amount, delivered, price
 * @param hours the hours left
 * @returns the order
 */
export function claimed(id, name, lines, hours) {
    return {
        id,
        name,
        state: 'CLAIMED',
        lines: lines.map(([itemName, amount, delivered, price]) => ({item: item(itemName, amount), delivered, price})),
        claimed: new Date(Date.now() - 3_600_000).toISOString(),
        deadline: new Date(Date.now() + hours * 3_600_000).toISOString(),
    }
}

/** The positions of the mock company; Market stalls is the one the stream delivers to. */
export function positions() {
    return [
        claimed(201, 'Harbour lighthouse', [['Sea Lantern', 48, 30, 980], ['Prismarine', 512, 318, 3000]], 14),
        claimed(202, 'Castle roof', [['Deepslate Tiles', 2304, 806, 7140]], 31),
        claimed(203, 'Market stalls', [['Spruce Planks', 960, 800, 1150]], 3),
        claimed(204, 'Guard towers', [['Stone Bricks', 864, 104, 2100]], 44),
    ]
}

/**
 * The snapshot of Wren, owner of Stonebridge Builders, before or after the stream's delivery and deposit.
 *
 * @param session the session id
 * @param delivered whether the delivery and the deposit of the stream are in it
 * @returns the snapshot
 */
export function snapshot(session, delivered = false) {
    const members = MEMBERS.map((_, index) => member(index))
    const orders = positions()
    if (delivered) {
        members[1] = member(1, 3240 + 64)
        orders[2].lines[0].delivered += 64
    }
    return {
        type: 'snapshot',
        v: 1,
        session,
        node: NODE,
        seq: 41,
        player: {uuid: member(0).uuid, name: 'Wren', company: 7, permissions: ['OWNER'], invites: [], unreadInbox: delivered ? 3 : 2},
        company: {
            id: 7,
            name: 'Stonebridge Builders',
            tag: 'BLD',
            color: '#FFB74D',
            description: 'Builders of walls, roofs and harbours. Applications open.',
            joinMode: 'APPLICATIONS',
            founded: '2025-03-14T18:00:00Z',
            level: {level: 3, name: 'Business', progress: 0.68},
            members,
            treasury: {balance: delivered ? 19600 : 18450},
            orders,
            alerts: [],
        },
        orders: [
            {
                id: 88,
                name: 'Redstone lab',
                state: 'CLAIMED',
                lines: [{item: item('Redstone Dust', 1536), delivered: 400, price: 1290}],
                created: '2026-10-20T08:00:00Z',
                waiting: [],
            },
            {
                id: 89,
                name: 'Garden lights',
                state: 'DELIVERED',
                lines: [{item: item('Lantern', 120), delivered: 120, price: 540}],
                created: '2026-10-18T08:00:00Z',
                waiting: [item('Lantern', 120)],
            },
        ],
        rankings: {
            ranking: 'orders',
            range: '30d',
            page: 1,
            pages: 1,
            entries: [
                {place: 1, company: 3, name: 'Copperworks', value: 61},
                {place: 2, company: 4, name: 'Deepcore Mining', value: 55},
                {place: 3, company: 5, name: 'Harbour Guild', value: 48},
                {place: 4, company: 7, name: 'Stonebridge Builders', value: 41},
            ],
            own: {place: 4, company: 7, name: 'Stonebridge Builders', value: 41},
        },
    }
}

/** The market of the mock, most ordered first. */
export const MARKET = [
    ['Iron Ingot', 8.7, 6, 11.5, 9400], ['Oak Log', 1.2, 0.8, 1.6, 22800], ['Stone Bricks', 2.43, 1.9, 3.1, 14300],
    ['Diamond', 96, 80, 120, 310], ['Wheat', 1.5, 1, 2, 6400], ['Redstone Dust', 0.9, 0.5, 1.4, 18100],
    ['Glass', 0.6, 0.4, 0.9, 11000], ['Bookshelf', 24.1, 18, 30, 840], ['Sea Lantern', 14.2, 11, 18, 1200],
    ['Deepslate Tiles', 3.1, 2.4, 3.9, 7700], ['Polished Blackstone', 3.2, 2.5, 4, 5200], ['Beacon', undefined, undefined, undefined, 2],
].map(([name, avg, min, max, volume]) => ({item: item(name, 1), avg, min, max, volume}))

/** The open orders of the mock order book. */
export const BOOK = [
    [1, 'Spawn walls', 'Stone Bricks', 1728, 4200], [2, 'Beacon pyramid', 'Iron Ingot', 1476, 13400],
    [3, 'Library shelves', 'Bookshelf', 96, 2520], [4, 'Farm restock', 'Wheat', 640, 1080],
    [5, 'Nether hub floor', 'Polished Blackstone', 1024, 3600], [6, 'Harbour glass', 'Glass', 2048, 1100],
    [7, 'Ore stock', 'Diamond', 32, 3300], [8, 'Circuit parts', 'Redstone Dust', 1536, 1290],
    [9, 'Lamp posts', 'Lantern', 120, 540], [10, 'Firewood', 'Oak Log', 2304, 2900],
    [11, 'Tower roof', 'Deepslate Tiles', 1152, 3700], [12, 'Aquarium', 'Sea Lantern', 64, 860],
].map(([id, name, itemName, amount, price]) => ({
    id,
    name,
    state: 'UNCLAIMED',
    lines: [{item: item(itemName, amount), delivered: 0, price}],
    created: new Date(Date.now() - id * 3_600_000).toISOString(),
    deadline: null,
    expires: new Date(Date.now() + (48 - id) * 3_600_000 + 1_800_000).toISOString(),
}))

/** The inbox of the mock, newest first. */
export const INBOX = [
    [1, 'MY_ORDER_ACCEPTED', false, 'Your order Redstone lab is being worked on.'],
    [2, 'APPLICATION_RECEIVED', false, 'A player applied to Stonebridge Builders: "I build a lot of farms."'],
    [3, 'PAYMENT', true, 'You were paid 1,240.00 for Bakery walls. 62.00 went to the treasury.'],
    [4, 'LEVEL_UP', true, 'Stonebridge Builders reached level 3: Business.'],
    [5, 'MY_ORDER_DONE', true, 'Your order Garden lights was completed. Collect it in game.'],
].map(([id, type, read, text]) => ({id, type, read, text, created: new Date(Date.now() - id * 1_800_000).toISOString()}))

/** The treasury log of the mock, newest first: deposits by Kestrel_, withdrawals by Wren, payout shares. */
export const TREASURY_LOG = [14200, 14400, 14100, 14900, 15300, 15000, 15800, 16100, 15900, 16600, 17000, 16800, 17500, 17900, 18450]
    .map((balance, index, all) => {
        const delta = index === 0 ? balance : balance - all[index - 1]
        const kind = delta < 0 ? 'WITHDRAWAL' : index % 2 === 0 ? 'DEPOSIT' : 'PAYOUT_SHARE'
        const actor = kind === 'WITHDRAWAL' ? member(0) : kind === 'DEPOSIT' ? member(1) : null
        return {
            id: index + 1,
            time: new Date(Date.now() - (all.length - index) * 86_400_000).toISOString(),
            kind,
            amount: delta,
            balance,
            ...(actor ? {actor: {uuid: actor.uuid, name: actor.name}} : {order: 201}),
        }
    })
    .reverse()
