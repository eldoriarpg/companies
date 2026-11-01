/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {randomUUID} from 'node:crypto'
import {createServer} from 'node:http'
import {WebSocketServer} from 'ws'
import {
    applications, BOOK, CATALOGUE, CODE, DISCONTINUED, FEATURES, history, INBOX, KNOWN_ITEMS, MARKET, member, NODE, OTHER_NODE, positions, SESSIONS,
    snapshot, TREASURY_LOG,
} from './data.mjs'

/** Where the mock listens, unless `MOCK_PORT` says otherwise. */
const PORT = Number(process.env.MOCK_PORT ?? 8787)

/**
 * The session cookie's name. The gateway names it `__Host-companies_session`, a name only a `Secure` cookie may carry;
 * over plain HTTP, as here, it runs with `COOKIE_SECURE=false` and names it `companies_session`.
 */
const COOKIE = 'companies_session'

/** The dashboard's public address, whose origin alone may send requests the cookie authorises, as at the gateway. */
const PUBLIC_ORIGIN = new URL(process.env.MOCK_PUBLIC_URL ?? 'http://localhost:4000').origin

/** The turnstile token the mock's challenge hands out, see `e2e/session.ts`. */
const TURNSTILE_TOKEN = 'turnstile-ok'

/** What happened to each logged in session: whether its stream delivered, whether its server was discontinued. */
const STATES = new Map()

/**
 * A stand-in for the gateway, for the end-to-end stories: the browser API of `src/api/gateway.ts` and the node
 * connection, with fixed data. Every waiting session of `SESSIONS` takes the code `CODE` and plays a story of its own,
 * see there. The cookie names the short id and a random part, so stories running at once never share a session.
 */
const server = createServer(async (request, response) => {
    const url = new URL(request.url ?? '/', 'http://mock')
    const body = request.method === 'POST' ? await readBody(request) : {}
    const answer = foreign(request) && request.method === 'POST'
        ? error(403, 'UNAUTHORIZED', {message: 'Requests from other sites are refused'})
        : route(request.method ?? 'GET', url.pathname, body, sessionOf(request))
    response.writeHead(answer.status, {'content-type': 'application/json', 'cache-control': 'no-store', ...answer.headers})
    response.end(answer.body === undefined ? '' : JSON.stringify(answer.body))
})

/**
 * Whether a request comes from another site, judged as the gateway judges it: by its `Origin`, or without one by
 * `Sec-Fetch-Site`.
 *
 * @param request the request
 * @returns true for a foreign request
 */
function foreign(request) {
    const origin = request.headers.origin
    if (origin) return origin.toLowerCase() !== PUBLIC_ORIGIN
    const site = request.headers['sec-fetch-site']
    return site !== undefined && site !== 'same-origin' && site !== 'none'
}

/**
 * The JSON body of a request.
 *
 * @param request the request
 * @returns the parsed body, or an empty object
 */
async function readBody(request) {
    let text = ''
    for await (const chunk of request) text += chunk
    try {
        return JSON.parse(text || '{}')
    } catch {
        return {}
    }
}

/**
 * The session the cookie names, with its story.
 *
 * @param request the request
 * @returns the cookie's value, the short id and the story, or nothing without a session
 */
function sessionOf(request) {
    const cookie = request.headers.cookie ?? ''
    const value = cookie.split(';').map(part => part.trim().split('=')).find(([name]) => name === COOKIE)?.[1]
    const shortId = value?.split('.')[0]
    if (!value || !SESSIONS[shortId]) return null
    if (!STATES.has(value)) STATES.set(value, {delivered: false, discontinued: false})
    return {id: value, shortId, story: SESSIONS[shortId], state: STATES.get(value)}
}

/**
 * An error answer.
 *
 * @param status the HTTP status
 * @param error the error code
 * @param extra more fields
 * @returns the answer
 */
function error(status, error, extra = {}) {
    return {status, body: {error, ...extra}}
}

/**
 * Answers one request.
 *
 * @param method the method
 * @param path the path
 * @param body the JSON body
 * @param session the cookie's session
 * @returns status, headers and body
 */
function route(method, path, body, session) {
    const pending = path.match(/^\/api\/v1\/s\/([^/]+)(\/login)?$/)
    if (pending) return login(method, decodeURIComponent(pending[1]), Boolean(pending[2]), body)
    if (!path.startsWith('/api/v1/me/')) return error(404, 'UNKNOWN_SESSION')
    if (!session) return error(401, 'UNAUTHORIZED', {message: 'Log in with the code from the game'})
    const {story, state} = session
    switch (`${method} ${path}`) {
        case 'GET /api/v1/me/server':
            return {status: 200, body: serverInfo(session)}
        case 'GET /api/v1/me/snapshot':
            if (story.snapshot) return error(story.snapshot, 'SERVER_OFFLINE', {message: 'The server is not connected right now'})
            if (state.discontinued) return error(410, 'DISCONTINUED', {message: DISCONTINUED.message, version: DISCONTINUED})
            return {status: 200, body: story.company === false ? companyless(session.id) : snapshot(session.id, state.delivered)}
        case 'POST /api/v1/me/actions':
            if (story.offline) return error(504, 'SERVER_OFFLINE', {message: 'The server did not answer in time'})
            return {status: 200, body: action(body.name, body.args ?? {}, state, story)}
        case 'POST /api/v1/me/logout':
            return {status: 204, headers: {'set-cookie': `${COOKIE}=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict`}}
        default:
            return error(404, 'UNKNOWN_SESSION')
    }
}

/** The order of the book the server placed itself, marked while the session's server marks such orders. */
const SERVER_ORDER = 12

/** The order of the book that is a chunk of the goal {@link GOAL}, while the session's server offers events. */
const CHUNK_ORDER = 10

/** The event offers of the mock: a goal for oak logs and a contest. */
const GOAL = 41
const CONTEST = 42
/** A contest that closed with its prizes settled, opened from its address only. */
const CLOSED_CONTEST = 43
/** A contest the company joined on another device: the page first shows it out of date. */
const JOINED_ELSEWHERE = 44

/**
 * An event offer as the Events page lists it.
 *
 * @param state the session's state, which keeps whether the company joined the contest
 * @param id the offer
 * @returns the offer
 */
function eventRow(state, id) {
    const hours = h => new Date(Date.now() + h * 3_600_000).toISOString()
    return id === GOAL
        ? {id, kind: 'goal', name: 'Bridge logs', item: {name: 'Oak Log', icon: 'oak_log'}, amount: 5000, delivered: 2250, percent: 45, unitPrice: 2, state: 'active', starts: hours(-24), ends: hours(48), prizeTotal: 0, joined: false}
        : {id, kind: 'contest', name: 'Log race', item: {name: 'Spruce Log', icon: 'spruce_log'}, amount: 3000, delivered: 600, percent: 20, unitPrice: 1.5, state: 'active', starts: hours(-2), ends: hours(22), prizeTotal: 750, place: state.joinedContest ? 2 : null, joined: Boolean(state.joinedContest)}
}

/**
 * An event offer as its page shows it.
 *
 * @param state the session's state
 * @param id the offer
 * @returns the details
 */
function eventDetails(state, id) {
    if (id === CLOSED_CONTEST) {
        const offer = {...eventRow(state, CONTEST), id, name: 'Harvest race', state: 'ended', percent: 100, delivered: 3000, place: 2, joined: false}
        return {
            offer,
            contributors: [],
            ranking: [{place: 1, name: 'Copperworks', delivered: 2000, active: true, own: false}, {place: 2, name: 'Stonebridge Builders', delivered: 1000, active: false, own: true}],
            prizes: [{place: 1, amount: 500, winner: 'Copperworks', state: 'paid'}, {place: 2, amount: 250, winner: 'Stonebridge Builders', state: 'paid'}],
            own: {company: 'Stonebridge Builders', joined: false, delivered: 1000, paid: 1500, place: 2},
            member: true,
            canManage: true,
        }
    }
    if (id === JOINED_ELSEWHERE) {
        const joined = Boolean(state.relaySeen)
        const offer = {...eventRow(state, CONTEST), id, name: 'Relay race', place: null, joined}
        return {offer, contributors: [], ranking: [], prizes: [], own: {company: 'Stonebridge Builders', joined, delivered: 0, paid: 0, place: null}, member: true, canManage: true}
    }
    const offer = eventRow(state, id)
    if (id === GOAL) {
        return {offer, chunkSize: 250, contributors: [{rank: 1, name: 'Stonebridge Builders', alone: false, delivered: 1500, chunks: 6}, {rank: 2, name: null, alone: true, delivered: 750, chunks: 3}], ranking: [], prizes: [], member: true, canManage: true}
    }
    const ranking = [{place: 1, name: 'Copperworks', delivered: 400, active: true, own: false}]
    if (state.joinedContest) ranking.push({place: 2, name: 'Stonebridge Builders', delivered: 200, active: true, own: true})
    return {
        offer,
        contributors: [],
        ranking,
        prizes: [{place: 1, amount: 500, state: 'pending'}, {place: 2, amount: 250, state: 'pending'}],
        own: {company: 'Stonebridge Builders', joined: Boolean(state.joinedContest), delivered: state.joinedContest ? 200 : 0, paid: state.joinedContest ? 300 : 0, place: state.joinedContest ? 2 : null},
        member: true,
        canManage: true,
    }
}

/**
 * A server event as the Events page lists it.
 *
 * @returns the event
 */
function playerServerEvent() {
    const hours = h => new Date(Date.now() + h * 3_600_000).toISOString()
    return {id: 7, kind: 'reward_multiplier', name: 'Harvest week', value: 1.5, materials: ['WHEAT'], levels: [], state: 'active', starts: hours(-1), ends: hours(47), budgetLeft: 4200}
}

/**
 * The server events of the admin tab, kept per session.
 *
 * @param state the session's state
 * @returns the events by id
 */
function serverEvents(state) {
    state.serverEvents ??= {
        7: {
            id: 7, kind: 'reward_multiplier', name: 'Harvest week', value: 1.5, materials: ['WHEAT'], levels: [], state: 'active',
            starts: '2026-10-29T18:00:00Z', ends: '2026-11-01T18:00:00Z', funding: 'minted', account: null, budget: 5000, spent: 800,
            template: 'harvest', createdBy: 'events.yml', created: '2026-10-28T18:00:00Z', closed: null, closedBy: null, offers: [{id: GOAL, name: 'Bridge logs'}],
        },
        6: {
            id: 6, kind: 'tax_reduction', name: 'Quiet weekend', value: 5, materials: [], levels: [], state: 'ended',
            starts: '2026-10-24T18:00:00Z', ends: '2026-10-26T18:00:00Z', funding: null, account: null, budget: null, spent: 0,
            template: null, createdBy: 'Ada', created: '2026-10-24T10:00:00Z', closed: '2026-10-26T18:00:00Z', closedBy: '#missed', offers: [],
        },
    }
    return state.serverEvents
}

/**
 * The event offers of a session as admins see them, made on first use.
 *
 * @param state the session's state
 * @returns the offers by id
 */
function adminOffers(state) {
    state.adminOffers ??= {
        [GOAL]: {
            id: GOAL, kind: 'goal', name: 'Bridge logs', item: {name: 'Oak Log', icon: 'oak_log'}, line: 'material', state: 'active', amount: 5000, delivered: 2250, percent: 45, unitPrice: 2,
            chunkSize: 250, openChunks: 3, waiting: 500, taken: 250, source: 'account', account: 'Treasury', budget: 10000, spent: 6000, left: 4000, held: null, prizeMoney: [], prizeTotal: 0,
            createdBy: 'Ada', created: '2026-10-27T08:00:00Z', starts: '2026-10-27T08:00:00Z', ends: '2026-10-31T08:00:00Z',
            contributors: [{rank: 1, company: 7, name: 'Stonebridge Builders', alone: false, delivered: 1500, chunks: 6}, {rank: 2, company: null, name: 'Orla', alone: true, delivered: 750, chunks: 3}],
            ranking: [], prizes: [],
        },
        [CONTEST]: {
            id: CONTEST, kind: 'contest', name: 'Log race', item: {name: '#logs', icon: null, criteria: '#logs'}, line: 'criteria', state: 'ended', amount: 3000, delivered: 3000, percent: 100, unitPrice: 1.5,
            waiting: 0, taken: 0, source: 'minted', budget: 5250, spent: 5250, left: 0, prizeMoney: [500, 250], prizeTotal: 750,
            createdBy: 'Ada', created: '2026-10-20T08:00:00Z', starts: '2026-10-20T08:00:00Z', ends: '2026-10-22T08:00:00Z', closed: '2026-10-22T08:00:00Z', settled: '2026-10-22T08:01:00Z',
            contributors: [],
            ranking: [{place: 1, company: 9, name: 'Copperworks', delivered: 2000, paid: 3000, active: true, joined: '2026-10-20T09:00:00Z'}, {place: 2, company: 7, name: 'Stonebridge Builders', delivered: 1000, paid: 1500, active: true, joined: '2026-10-20T10:00:00Z'}],
            prizes: [{place: 1, amount: 500, company: 9, name: 'Copperworks', state: 'paid'}, {place: 2, amount: 250, company: 7, name: 'Stonebridge Builders', state: 'paying'}],
        },
    }
    return state.adminOffers
}

/**
 * An event offer as the admin list shows it.
 *
 * @param offer the offer's record
 * @returns the row
 */
function adminOfferRow(offer) {
    const {id, kind, name, item, state, delivered, amount, percent, source, account, budget, spent, left, starts, ends} = offer
    return {id, kind, name, item, state, delivered, amount, percent, source, account, budget, spent, left, starts, ends}
}

/**
 * Creates an event offer as the plugin would, refusing an unknown material as it words it.
 *
 * @param state the session's state
 * @param args the draft
 * @returns the answer
 */
function createOffer(state, args) {
    if (args.material === 'not_an_item') {
        return {type: 'action.result', v: 1, ok: false, reason: 'companyadmin.offers.error.material', message: 'not_an_item is no item that can be ordered.'}
    }
    const offers = adminOffers(state)
    const id = Math.max(...Object.keys(offers).map(Number)) + 1
    const now = new Date().toISOString()
    const prizes = args.prizes ?? []
    const budget = args.budget ?? args.amount * args.price + prizes.reduce((sum, prize) => sum + prize, 0)
    offers[id] = {
        id, kind: args.kind, name: args.name, item: {name: args.material ?? args.criteria, icon: args.material ?? null}, line: args.material ? 'material' : 'criteria',
        state: args.start > 0 ? 'scheduled' : 'active', amount: args.amount, delivered: 0, percent: 0, unitPrice: args.price,
        chunkSize: args.chunk ?? null, openChunks: args.open ?? null, waiting: 0, taken: 0, source: args.money === 'minted' ? 'minted' : 'account',
        account: args.money === 'minted' ? null : args.money, budget, spent: 0, left: budget, prizeMoney: prizes, prizeTotal: prizes.reduce((sum, prize) => sum + prize, 0),
        createdBy: 'Ada', created: now, starts: now, ends: new Date(Date.now() + args.hours * 3_600_000).toISOString(), contributors: [], ranking: [], prizes: [],
    }
    return {type: 'action.result', v: 1, ok: true, data: offers[id]}
}

/** The orders the server placed itself, as the admin list shows them. */
const SYSTEM_ORDERS = [
    {order: 801, name: 'Royal smithy', rule: 'iron-weekly', kind: 'DEFINED', state: 'UNCLAIMED', placed: '2026-10-26T08:00:00Z', paid: 512, account: 'Collector'},
    {order: 802, name: 'Dirt', rule: 'guessed', kind: 'GUESSED', state: 'CLAIMED', placed: '2026-10-26T09:00:00Z', paid: 102.4, account: null},
]

/** Why the server placed its orders, by order id. */
const SYSTEM_DECISIONS = {
    801: {order: 801, rule: 'iron-weekly', kind: 'DEFINED', placed: '2026-10-26T08:00:00Z', paid: 512, account: 'Collector', record: {rule: 'iron-weekly', material: 'IRON_INGOT', amount: 256, mode: 'FIXED', unit: 2, total: 512}},
    802: {order: 802, rule: 'guessed', kind: 'GUESSED', placed: '2026-10-26T09:00:00Z', paid: 102.4, account: null, record: {material: 'DIRT', amount: 64, score: 0.1, rank: 1, unit: 1.6, learning: {base: 0.8, takeRate: 0.5}}},
}

/** The defined rules of the server's own orders. */
const SYSTEM_RULES = [
    {id: 'iron-weekly', name: 'Royal smithy', material: 'IRON_INGOT', amount: 256, price: {mode: 'FIXED', value: 2}},
    {
        id: 'armoury',
        name: 'Royal armoury',
        items: [
            {material: 'IRON_SWORD', criteria: '', item: null, name: '', resolve: 'ANY'},
            {material: '', criteria: '', item: 'AAECAw==', name: 'Royal blade', resolve: 'ANY'},
        ],
        pick: 'rotate',
        amount: 1,
        price: {mode: 'FIXED', value: 300},
    },
]

/** What guessed orders learned per material. */
const SYSTEM_LEARNED = [{material: 'DIRT', posted: 6, taken: 4, expired: 1, minutesToTake: 42, factor: 0.75, offset: -0.05, takeRate: 0.71}]

/**
 * The server of a session as `GET /api/v1/me/server` and the `server` stream event send it.
 *
 * @param session the session
 * @returns the server
 */
function serverInfo(session) {
    const version = session.state.discontinued ? DISCONTINUED : session.story.version
    const admin = Boolean(session.story.admin)
    const features = admin
        ? [...(session.story.features ?? FEATURES), 'admin.orders', 'admin.players', 'admin.companies', 'admin.levels', 'admin.boards', 'admin.nodes', 'admin.config', 'admin.settings', 'admin.setup', 'admin.systemorders', 'admin.offers', 'admin.events', 'admin.jobs']
        : session.story.features ?? FEATURES
    const nodes = admin ? [{uid: PRIMARY_NODE, role: 'PRIMARY', name: 'survival'}, {uid: SECONDARY_NODE, role: 'SECONDARY'}] : []
    return {server: {name: 'Mossvale SMP'}, features, limits: {maxPageSize: 50}, version, deprecated: [], online: !session.story.offline, admin, nodes}
}

/**
 * The waiting session and its login.
 *
 * @param method the method
 * @param shortId the short id
 * @param submit whether the code is sent
 * @param body the JSON body
 * @returns status, headers and body
 */
function login(method, shortId, submit, body) {
    const story = SESSIONS[shortId]
    if (!story) return error(404, 'UNKNOWN_SESSION', {message: 'No session is waiting for this link'})
    if (!submit && method === 'GET') {
        const expiresAt = new Date(Date.now() + 5 * 60_000).toISOString()
        return {status: 200, body: {server: {name: 'Mossvale SMP'}, expiresAt, attemptsLeft: 5, turnstileSiteKey: story.turnstile, version: story.version}}
    }
    if (story.login === 'discontinued') return error(410, 'DISCONTINUED', {message: DISCONTINUED.message, version: DISCONTINUED})
    if (story.turnstile && body.turnstileToken !== TURNSTILE_TOKEN) return error(403, 'TURNSTILE_FAILED')
    if (body.code !== CODE) {
        if (story.login === 'locked') return error(410, 'TOO_MANY_ATTEMPTS', {message: 'Too many wrong codes'})
        return error(401, 'WRONG_CODE', {attemptsLeft: 4})
    }
    return {status: 204, headers: {'set-cookie': `${COOKIE}=${shortId}.${randomUUID()}; Path=/; HttpOnly; SameSite=Strict`}}
}

/**
 * The snapshot of a player without a company.
 *
 * @param session the session id
 * @returns the snapshot
 */
function companyless(session) {
    const body = snapshot(session)
    delete body.company
    return {...body, player: {...body.player, company: null, permissions: []}}
}

/** The order the mock's admin looks at. */
const ADMIN_ORDER = {
    id: 301,
    name: 'Harbour wall',
    state: 'CLAIMED',
    owner: {uuid: '00000000-0000-0000-0000-0000000000a1', name: 'Orla'},
    company: 'Stonebridge Builders',
    created: '2026-10-20T08:00:00Z',
    deadline: '2026-10-27T08:00:00Z',
    removal: false,
    price: 1100,
    fee: 55,
    lines: [{item: {material: 'minecraft:stone_bricks', name: 'Stone Bricks', amount: 512, icon: 'stone_bricks'}, delivered: 200, price: 1100, workers: {Kestrel_: 120, mossycobble: 80}}],
    refundOnCancel: 1155,
    keptOnCancel: 1155,
    refundOnDeliver: 670,
    charged: true,
}

/** The most items an order may hold on the mock server. */
const MAX_ITEMS = 512

/**
 * What an order costs on the mock server: its lines at their prices and a fee of five percent. More than
 * {@link MAX_ITEMS} items are refused as the plugin words it.
 *
 * @param args the order
 * @returns the price, fee and total, or the refusal
 */
function quoteOf(args) {
    const items = args.lines.reduce((sum, line) => sum + line.amount, 0)
    if (items > MAX_ITEMS) return {refusal: `An order may hold at most ${MAX_ITEMS} items.`}
    const price = args.lines.reduce((sum, line) => sum + line.amount * line.unitPrice, 0)
    const fee = Math.round(price * 5) / 100
    return {price, fee, total: price + fee}
}

/** The order the mock refuses to accept, as a company at its limit would hear it. */
const LIMITED_ORDER = 3

/** The primary node of the mock network. */
const PRIMARY_NODE = '4f6c1f0e-2b7a-4c39-9a51-0d6f2c7e9b10'

/** The secondary node of the mock network. */
const SECONDARY_NODE = '9a2d7c41-6b0e-4f3a-8c15-2e7b9d0f4a63'

/**
 * The NPCs bound to boards on a node of a session, made on first use.
 *
 * @param state the session's state
 * @param node the node, none for the primary
 * @returns the bindings
 */
function npcs(state, node) {
    state.npcs ??= {}
    state.npcs[node ?? PRIMARY_NODE] ??= []
    return state.npcs[node ?? PRIMARY_NODE]
}

/** Who stored the revisions of a settings file, newest first. */
const SETTINGS_HISTORY = [
    {revision: 2, actor: 'Ada', node: PRIMARY_NODE, changed: '2026-10-25T09:00:00Z'},
    {revision: 1, actor: 'startup', node: PRIMARY_NODE, changed: '2026-10-24T08:00:00Z'},
]

/**
 * A shared settings file as the settings view shows it.
 *
 * @param file the file
 * @returns the file with a few settings
 */
function settingsFile(file) {
    if (file === 'company.yml') {
        return {file, revision: 1, node: PRIMARY_NODE, applied: 1, editedHere: false, editable: true, behind: [], settings: [
            {section: 'company', key: 'foundingPrice', type: 'DECIMAL', choices: [], restart: false, value: 20000, overridden: false, min: 0},
        ]}
    }
    if (file === 'order.yml') {
        return {file, revision: 1, node: PRIMARY_NODE, applied: 1, editedHere: false, editable: true, behind: [], settings: [
            {section: 'fee', key: 'fee.fixed', type: 'DECIMAL', choices: [], restart: false, value: 0, overridden: false, min: 0},
        ]}
    }
    if (file !== 'config.yml') {
        return {file, revision: 1, node: PRIMARY_NODE, applied: 1, editedHere: false, editable: true, behind: [], settings: [
            {section: 'orders', key: 'maxOrders', type: 'WHOLE', choices: [], restart: false, value: 7, overridden: false},
        ]}
    }
    return {file, revision: 2, node: PRIMARY_NODE, applied: 2, editedHere: false, editable: true, behind: [SECONDARY_NODE], settings: [
        {section: 'general', key: 'generalSettings.language', type: 'CHOICE', choices: ['en_US', 'de_DE', 'fr_FR'], restart: false, value: 'en_US', overridden: false},
        {section: 'general', key: 'generalSettings.timezone', type: 'TEXT', choices: [], restart: false, value: '', overridden: false},
        {section: 'general', key: 'generalSettings.messageBlocking', type: 'FLAG', choices: [], restart: true, value: true, overridden: true},
        {section: 'discord', key: 'discord.enabled', type: 'FLAG', choices: [], restart: false, value: true, overridden: false},
        {section: 'discord', key: 'discord.username', type: 'TEXT', choices: [], restart: false, value: 'Companies', overridden: false},
        {section: 'discord', key: 'discord.url', type: 'SECRET', choices: [], restart: false, secret: {set: true, source: 'environment'}, overridden: false},
        {section: 'dashboard', key: 'dashboard.branding.icon', type: 'TEXT', choices: [], restart: false, value: 'server-icon.png', overridden: false},
        {section: 'dashboard', key: 'dashboard.features', type: 'FLAG_MAP', choices: [], restart: false, value: {market: false, inbox: true}, overridden: false},
    ]}
}

/** The pages of the guided setup with the settings they show. */
const SETUP_PAGES = [
    {id: 'economy', fields: [{file: 'company.yml', key: 'foundingPrice'}, {file: 'order.yml', key: 'fee.fixed'}]},
    {id: 'levels', fields: []},
    {id: 'language', fields: [{file: 'config.yml', key: 'generalSettings.language'}]},
    {id: 'permissions', fields: []},
]

/**
 * The guided setup of a session, made on first use.
 *
 * @param state the session's state
 * @returns the pages with their state and whether the reminder was dismissed
 */
function setupOf(state) {
    state.setup ??= {progress: {}}
    const progress = state.setup.progress
    return Object.assign(state.setup, {
        pages: SETUP_PAGES.map(page => ({...page, state: progress[page.id] ?? 'open'})),
        dismissed: progress.banner === 'dismissed',
        editable: true,
    })
}

/**
 * A settings file as the session changed it, made on first use.
 *
 * @param state the session's state
 * @param file the file
 * @returns the file
 */
function storedSettings(state, file) {
    state.settings ??= {}
    state.settings[file] ??= settingsFile(file)
    return state.settings[file]
}

/**
 * What a save or rollback answers.
 *
 * @param status what happened
 * @param revision the stored or latest revision
 * @param extra the fields of a refused or outdated save
 * @returns the answer
 */
function settingsAnswer(status, revision, extra = {}) {
    return {status, revision, fields: {}, values: {}, changed: [], restart: [], applied: true, ...extra}
}

/**
 * Saves settings: a timezone on Mars and an emptied number are refused, and the first save of the Discord username meets a revision another
 * admin saved meanwhile.
 *
 * @param state the session's state
 * @param args the file, the revision edited and the changed values
 * @returns the answer
 */
function saveSettings(state, args) {
    const stored = storedSettings(state, args.file)
    if (args.values['generalSettings.timezone'] === 'Mars/Base') {
        return settingsAnswer('invalid', 0, {fields: {'generalSettings.timezone': 'timezone'}})
    }
    const negative = Object.keys(args.values).filter(key => typeof args.values[key] === 'number' && args.values[key] < 0)
    if (negative.length > 0) return settingsAnswer('invalid', 0, {fields: Object.fromEntries(negative.map(key => [key, 'range']))})
    const empty = Object.keys(args.values).filter(key => args.values[key] === null)
    if (empty.length > 0) return settingsAnswer('invalid', 0, {fields: Object.fromEntries(empty.map(key => [key, 'empty']))})
    if ('discord.username' in args.values && !state.settingsRaced) {
        state.settingsRaced = true
        stored.revision++
        stored.settings.find(entry => entry.key === 'discord.username').value = 'Other'
        return settingsAnswer('newer', stored.revision, {values: {'discord.username': 'Other'}, changed: ['discord.username']})
    }
    if (args.base !== stored.revision) return settingsAnswer('newer', stored.revision)
    for (const [key, value] of Object.entries(args.values)) {
        const entry = stored.settings.find(candidate => candidate.key === key)
        if (entry && entry.type !== 'SECRET') entry.value = value
    }
    stored.revision++
    stored.applied = stored.revision
    const restart = Object.keys(args.values).filter(key => stored.settings.find(entry => entry.key === key)?.restart)
    return settingsAnswer('saved', stored.revision, {restart})
}

/**
 * The servers in the database of a session, made on first use: both nodes and an old one.
 *
 * @param state the session's state
 * @returns the servers
 */
function servers(state) {
    state.servers ??= [
        {uid: PRIMARY_NODE, role: 'PRIMARY', version: '2.1.0', lastSeen: '2026-10-25T08:00:00Z', bus: 'Redis', current: true},
        {uid: SECONDARY_NODE, role: 'SECONDARY', version: '2.1.0', lastSeen: '2026-10-25T08:01:00Z', bus: 'Redis', current: false, behind: true, restart: 'config.yml boards.enabled'},
        {uid: '0b1c2d3e-4f50-4617-8293-a4b5c6d7e8f9', role: 'SECONDARY', version: '2.0.0', lastSeen: '2026-09-01T10:00:00Z', bus: '-', current: false},
    ]
    return state.servers
}

/**
 * A company level as the admin level editor shows it.
 *
 * @param level the level's number
 * @param name its name
 * @returns the level
 */
function adminLevel(level, name) {
    return {level, name, orderCount: level * 10, memberCount: level, earnedMoney: level * 1000, deliveredItems: level * 500, maxMembers: 5 + level * 5, maxOrders: level + 2, maxOrderSize: 0, deliveryHoursBonus: 0, taxPercent: null, maxAlerts: null, maxAreas: 1}
}

/**
 * The levels of a session, made on first use.
 *
 * @param state the session's state
 * @returns the levels
 */
function levels(state) {
    state.levels ??= [adminLevel(1, 'Workshop'), adminLevel(2, 'Guild')]
    return state.levels
}

/**
 * The jobs of the mock server: an open contract, a dig job the player's company works on, a contract of the player
 * that waits for their approval and one a company works on that asks for more time.
 *
 * @param state the session's state, which keeps the jobs as they change
 * @returns the jobs by id
 */
function jobsOf(state) {
    state.jobs ??= {
        11: {id: 11, kind: 'contract', state: 'open', title: 'Paint the barn', price: 120, fee: 6, expires: '2026-11-06T12:00:00Z', blocks: 0, mine: false, ours: false, description: 'Red, with white trim.'},
        12: {id: 12, kind: 'dig', state: 'taken', title: 'Cellar', price: 300, fee: 15, due: '2026-11-01T12:00:00Z', blocks: 250, world: 'world', center: '10 60 -4', mine: false, ours: true},
        13: {id: 13, kind: 'contract', state: 'done', title: 'Fence the field', price: 80, fee: 4, due: '2026-11-02T12:00:00Z', blocks: 0, mine: true, ours: false},
        14: {id: 14, kind: 'contract', state: 'taken', title: 'Roof the hall', price: 500, fee: 25, due: '2026-11-03T12:00:00Z', blocks: 0, mine: true, ours: false,
            request: {hours: 24, reason: 'The hall is bigger than drawn.'}},
    }
    return state.jobs
}

/**
 * @param job a job of the mock server
 * @returns the job as the Jobs page lists it
 */
function jobRow(job) {
    const row = {...job}
    for (const field of ['description', 'dispute', 'disputeByOwner', 'rejected', 'request', 'extendedHours', 'requestsLeft']) delete row[field]
    return row
}

/**
 * @param job a job of the mock server
 * @returns the job with what its page shows
 */
function jobDetails(job) {
    return {
        job: jobRow(job),
        description: job.description ?? null,
        rejected: job.rejected ?? null,
        dispute: job.dispute ?? null,
        disputeByOwner: job.disputeByOwner ?? false,
        settled: null,
        approveAt: job.state === 'done' ? '2026-11-05T12:00:00Z' : null,
        stock: {},
        canManage: true,
        takeable: job.state === 'open' && !job.mine,
        extendedHours: job.extendedHours ?? 0,
        hoursLeft: job.state === 'taken' ? 312 - (job.extendedHours ?? 0) : 0,
        requestsLeft: job.state === 'taken' ? (job.requestsLeft ?? 3) : 0,
        request: job.state === 'taken' ? (job.request ?? null) : null,
    }
}

/**
 * Gives a taken job more time, or notes and answers a company's request for it, as the plugin would.
 *
 * @param state the session's state
 * @param name the action
 * @param args its arguments
 * @param done answers the job afterwards
 * @param refused answers a refusal
 * @returns the answer
 */
function extensionStep(state, name, args, done, refused) {
    const job = jobsOf(state)[args.job]
    if (!job || job.state !== 'taken') return refused(`No company is working on job #${args.job} right now.`)
    const extend = hours => {
        job.extendedHours = (job.extendedHours ?? 0) + hours
        job.request = null
    }
    if (name === 'jobs.extend') extend(args.hours)
    if (name === 'jobs.extension.grant') extend(job.request.hours)
    if (name === 'jobs.extension.refuse') job.request = null
    if (name === 'jobs.extension.ask') {
        job.request = {hours: args.hours, reason: args.reason}
        job.requestsLeft = (job.requestsLeft ?? 3) - 1
    }
    return done(jobDetails(job))
}

/**
 * @param state the session's state
 * @returns the lists of the Jobs page
 */
function jobList(state) {
    const all = Object.values(jobsOf(state))
    return {
        open: all.filter(job => job.state === 'open' && !job.mine).map(jobRow),
        mine: all.filter(job => job.mine).map(jobRow),
        company: all.filter(job => job.ours).map(jobRow),
        inCompany: true,
        canManage: true,
        canPost: true,
    }
}

/** The state each step of a job leads to. */
const JOB_STEPS = {
    'jobs.take': {from: ['open'], to: 'taken', ours: true},
    'jobs.done': {from: ['taken'], to: 'done'},
    'jobs.approve': {from: ['taken', 'done', 'disputed'], to: 'paid'},
    'jobs.reject': {from: ['done'], to: 'taken'},
    'jobs.dispute': {from: ['taken', 'done'], to: 'disputed'},
    'jobs.withdraw': {from: ['disputed'], to: 'taken'},
    'jobs.cancel': {from: ['open'], to: 'cancelled'},
    'jobs.giveback': {from: ['taken', 'done'], to: 'open', ours: false},
    'jobs.end': {from: ['taken'], to: 'paid'},
    'jobs.describe': {from: ['open'], to: 'open'},
}

/**
 * Moves a job of the mock server a step on, as the plugin would.
 *
 * @param state the session's state
 * @param name the step's action
 * @param args its arguments
 * @param done answers the job afterwards
 * @param refused answers a refusal
 * @returns the answer
 */
function jobStep(state, name, args, done, refused) {
    const job = jobsOf(state)[args.job]
    const step = JOB_STEPS[name]
    if (!job || !step.from.includes(job.state)) return refused(`Job #${args.job} is not open anymore.`)
    job.state = step.to
    if (step.ours !== undefined) job.ours = step.ours
    if (name === 'jobs.reject') job.rejected = args.reason
    if (name === 'jobs.dispute') {
        job.dispute = args.reason
        job.disputeByOwner = job.mine
    }
    if (name === 'jobs.describe') job.description = args.text
    return done(jobDetails(job))
}

/**
 * @param state the session's state
 * @returns the disputed jobs an admin sees
 */
function disputes(state) {
    state.disputes ??= [{
        id: 31, kind: 'contract', title: 'Stable', description: 'Room for six horses.', price: 400, owner: 'Orla', company: 'Stonebridge Builders',
        openedBy: 'Orla', byOwner: true, reason: 'Nothing was built.', due: '2026-11-01T12:00:00Z', disputedAt: '2026-10-30T12:00:00Z', overdue: false, work: 0,
    }]
    return state.disputes
}

/**
 * The answer to an action. Accepting order 3 is refused as the plugin words it; every other action succeeds.
 *
 * @param name the action
 * @param args its arguments
 * @param state the session's state, which keeps the muted inbox categories
 * @returns the action result
 */
function action(name, args, state, story = {}) {
    const page = data => ({type: 'action.result', v: 1, ok: true, data: {page: args.page ?? 1, pages: 1, ...data}})
    const done = data => ({type: 'action.result', v: 1, ok: true, data})
    const refused = message => ({type: 'action.result', v: 1, ok: false, reason: 'error.refused', message})
    state.muted ??= new Set()
    state.applications ??= applications()
    switch (name) {
        case 'orders.accept':
            if (args.order === LIMITED_ORDER) {
                return {type: 'action.result', v: 1, ok: false, reason: 'error.orderLimit', message: 'Your company works on as many orders as its level allows.'}
            }
            return done({order: args.order})
        case 'admin.orders.info':
            if (args.order !== ADMIN_ORDER.id) return refused('There is no order with this id.')
            return done(ADMIN_ORDER)
        case 'admin.orders.search':
            if (args.player && args.player.toLowerCase() !== 'orla') return refused('This player is not known.')
            return done({target: 'Orla', page: 1, hasNext: false, orders: [{id: ADMIN_ORDER.id, name: ADMIN_ORDER.name, state: 'CLAIMED', owner: 'Orla', claimant: 'Stonebridge Builders', created: ADMIN_ORDER.created}]})
        case 'admin.orders.cancel':
            return done({order: args.order, refunded: args.refund ? 1155 : 0, collected: args.refund ? 0 : 1155, charged: true})
        case 'admin.orders.unclaim':
            return done({order: args.order, workers: 2})
        case 'admin.orders.deliver':
            return done({order: args.order, refund: 300})
        case 'admin.orders.extend':
            return done({order: args.order, hours: args.hours})
        case 'admin.players.lookup':
            if (String(args.player).toLowerCase() !== 'orla') return refused('This player is not known.')
            return done({player: ADMIN_ORDER.owner, company: 'Stonebridge Builders', owner: false, permissions: ['MANAGE_ORDERS'], orders: [], workerItems: 3, ownerItems: 0, unread: 2, latest: []})
        case 'admin.players.grant-money':
            return done({player: 'Orla', amount: `${Number(args.amount).toFixed(2)} coins`})
        case 'admin.players.grant-items':
            return done({player: 'Orla', amount: args.amount})
        case 'admin.companies.treasury':
            state.adminBalance ??= 18450
            return done({company: 'Stonebridge Builders', balance: state.adminBalance, share: 10, entries: []})
        case 'admin.companies.treasury.give':
            state.adminBalance = (state.adminBalance ?? 18450) + args.amount
            return done({company: 'Stonebridge Builders', balance: state.adminBalance})
        case 'admin.companies.treasury.take':
            if (args.amount > (state.adminBalance ?? 18450)) return refused('The treasury does not hold that much.')
            state.adminBalance = (state.adminBalance ?? 18450) - args.amount
            return done({company: 'Stonebridge Builders', balance: state.adminBalance})
        case 'admin.companies.rename':
            return done({company: args.name})
        case 'admin.companies.transfer-owner':
            return done({company: args.company, owner: args.player})
        case 'admin.companies.delete':
            return done({company: args.company})
        case 'admin.levels.list':
            return done({editable: true, levels: levels(state)})
        case 'admin.levels.create': {
            const created = adminLevel(levels(state).length + 1, 'New level')
            state.levels.push(created)
            return done(created)
        }
        case 'admin.levels.edit': {
            const level = levels(state).find(entry => entry.level === args.level)
            if (!level) return refused('There is no such level.')
            if (Number.isNaN(Number(args.values.order_count))) return refused('Orders done: That is not a number.')
            Object.assign(level, {name: args.values.name ?? level.name, orderCount: Number(args.values.order_count ?? level.orderCount)})
            return done(level)
        }
        case 'admin.levels.remove':
            state.levels = levels(state).filter(entry => entry.level !== args.level).map((entry, index) => ({...entry, level: index + 1}))
            return done({editable: true, levels: state.levels})
        case 'admin.levels.recalculate':
            return done({})
        case 'admin.boards.presets':
            return done({presets: [{name: 'iron', label: 'Iron', open: args.node === SECONDARY_NODE ? 1 : 4}]})
        case 'admin.boards.reload':
            return done({})
        case 'admin.boards.npc.list':
            return done({hooks: ['Citizens'], bindings: npcs(state, args.node)})
        case 'admin.boards.npc.link':
            if (args.npc === '99') return refused('Citizens has no NPC 99.')
            npcs(state, args.node).push({hook: args.hook, npc: args.npc, board: args.board})
            return done({hook: args.hook, npc: args.npc, board: args.board})
        case 'admin.boards.npc.unlink':
            state.npcs[args.node ?? PRIMARY_NODE] = npcs(state, args.node).filter(entry => entry.npc !== args.npc)
            return done({hook: args.hook, npc: args.npc, board: ''})
        case 'admin.nodes.list':
            return done({nodes: servers(state)})
        case 'admin.nodes.prices':
            return done({sources: [{id: 'chestshop', here: true, enabled: true, adminOnly: false, servers: 1, offers: 12, offeredItems: 4, trades: 30, tradedItems: 3, baselines: 5}]})
        case 'events.list':
            return done({offers: [{...eventRow(state, GOAL), event: 7}, eventRow(state, CONTEST)], serverEvents: [playerServerEvent()], member: true, canManage: true})
        case 'events.info':
            return [GOAL, CONTEST, CLOSED_CONTEST, JOINED_ELSEWHERE].includes(args.offer) ? done(eventDetails(state, args.offer)) : refused('This event offer does not exist, or it does not run right now.')
        case 'events.join':
            if (args.offer === JOINED_ELSEWHERE) {
                state.relaySeen = true
                return refused('Your company takes part in contest #44 already.')
            }
            if (state.joinedContest) return refused('Your company takes part in contest #42 already.')
            state.joinedContest = true
            return done(eventDetails(state, CONTEST))
        case 'events.leave':
            state.joinedContest = false
            return done(eventDetails(state, CONTEST))
        case 'jobs.list':
            return done(jobList(state))
        case 'jobs.info':
            return jobsOf(state)[args.job] ? done(jobDetails(jobsOf(state)[args.job])) : refused(`There is no job #${args.job}.`)
        case 'jobs.contract': {
            const id = 20
            jobsOf(state)[id] = {id, kind: 'contract', state: 'open', title: args.title, price: args.price, fee: 5, expires: '2026-11-06T12:00:00Z', blocks: 0, mine: true, ours: false, description: args.description}
            return done(jobDetails(jobsOf(state)[id]))
        }
        case 'jobs.take':
        case 'jobs.done':
        case 'jobs.approve':
        case 'jobs.reject':
        case 'jobs.dispute':
        case 'jobs.withdraw':
        case 'jobs.cancel':
        case 'jobs.giveback':
        case 'jobs.end':
        case 'jobs.describe':
            return jobStep(state, name, args, done, refused)
        case 'jobs.extend':
        case 'jobs.extension.grant':
        case 'jobs.extension.refuse':
        case 'jobs.extension.ask':
            return extensionStep(state, name, args, done, refused)
        case 'admin.jobs.disputes':
            return done({disputes: disputes(state)})
        case 'admin.jobs.settle':
            if (args.percent < 0 || args.percent > 100) return refused('The share must be between 0 and 100.')
            state.disputes = disputes(state).filter(dispute => dispute.id !== args.job)
            return done({disputes: state.disputes})
        case 'admin.offers.list':
            return done({offers: Object.values(adminOffers(state)).sort((a, b) => b.id - a.id).map(adminOfferRow)})
        case 'admin.offers.info':
            return adminOffers(state)[args.offer] ? done(adminOffers(state)[args.offer]) : refused(`There is no event offer #${args.offer}.`)
        case 'admin.offers.create':
            return createOffer(state, args)
        case 'admin.offers.pause':
        case 'admin.offers.resume':
        case 'admin.offers.cancel': {
            const offer = adminOffers(state)[args.offer]
            const to = {'admin.offers.pause': 'paused', 'admin.offers.resume': 'active', 'admin.offers.cancel': 'cancelled'}[name]
            const from = {'admin.offers.pause': ['scheduled', 'active'], 'admin.offers.resume': ['paused'], 'admin.offers.cancel': ['scheduled', 'active', 'paused']}[name]
            if (!from.includes(offer.state)) return refused(`The offer #${offer.id} is closed already.`)
            offer.state = to
            return done(offer)
        }
        case 'admin.offers.open': {
            const offer = adminOffers(state)[args.offer]
            offer.openChunks = args.count
            return done(offer)
        }
        case 'admin.offers.prize': {
            const offer = adminOffers(state)[args.offer]
            const prize = offer.prizes.find(entry => entry.place === args.place)
            prize.state = args.outcome
            return done(offer)
        }
        case 'admin.events.list':
            return done({events: Object.values(serverEvents(state)).sort((a, b) => b.id - a.id)})
        case 'admin.events.create': {
            if (!args.name) return refused('The event needs a name.')
            if (!(Number(args.value) > 0)) return refused(`${args.value} is no value for this kind.`)
            const events = serverEvents(state)
            const id = Math.max(...Object.keys(events).map(Number)) + 1
            const kind = args.kind.toLowerCase()
            events[id] = {
                id, kind, name: args.name, value: Number(args.value), materials: args.materials ? args.materials.split(',').map(part => part.trim().toUpperCase()) : [],
                levels: [], state: 'scheduled', starts: new Date(Date.now() + 7_200_000).toISOString(), ends: new Date(Date.now() + 93_600_000).toISOString(),
                funding: kind === 'reward_multiplier' ? args.funding.toLowerCase() : null, account: args.account ?? null, budget: args.budget ? Number(args.budget) : null,
                spent: 0, template: null, createdBy: 'Ada@dashboard', created: new Date().toISOString(), closed: null, closedBy: null, offers: [],
            }
            return done(events[id])
        }
        case 'admin.events.end':
        case 'admin.events.cancel': {
            const event = serverEvents(state)[args.event]
            if (!event) return refused(`There is no server event #${args.event}.`)
            if (name === 'admin.events.end' && event.state !== 'active') return refused(`The server event #${event.id} has not started; cancel it instead.`)
            if (!['scheduled', 'active'].includes(event.state)) return refused(`The server event #${event.id} is closed already.`)
            event.state = name === 'admin.events.end' ? 'ended' : 'cancelled'
            event.closed = new Date().toISOString()
            event.closedBy = 'Ada@dashboard'
            return done(event)
        }
        case 'admin.events.extend': {
            const event = serverEvents(state)[args.event]
            event.ends = new Date(new Date(event.ends).getTime() + 12 * 3_600_000).toISOString()
            return done(event)
        }
        case 'admin.events.budget': {
            const event = serverEvents(state)[args.event]
            event.budget = args.budget
            return done(event)
        }
        case 'admin.systemorders.list':
            return done({orders: SYSTEM_ORDERS})
        case 'admin.systemorders.info':
            return SYSTEM_DECISIONS[args.order] ? done(SYSTEM_DECISIONS[args.order]) : refused('companyadmin.systemorders.error.unknown')
        case 'admin.systemorders.stats':
            return done({materials: SYSTEM_LEARNED})
        case 'admin.systemorders.rules':
            return done({revision: state.ruleRevision ?? 1, editable: true, rules: state.rules ?? SYSTEM_RULES})
        case 'admin.systemorders.rules.save': {
            const errors = {}
            const ids = new Set()
            args.rules.forEach((rule, index) => {
                if (!rule.id) errors[`rules.${index}.id`] = 'missing'
                else if (ids.has(rule.id)) errors[`rules.${index}.id`] = 'duplicate'
                ids.add(rule.id)
            })
            if (Object.keys(errors).length > 0) return done({result: 'invalid', revision: 0, errors})
            state.rules = args.rules
            state.ruleRevision = (state.ruleRevision ?? 1) + 1
            return done({result: 'saved', revision: state.ruleRevision, errors: {}, applied: true})
        }
        case 'admin.nodes.remove':
            state.servers = servers(state).filter(entry => entry.uid !== args.uid)
            return done({nodes: state.servers})
        case 'admin.config.sync':
            return done({})
        case 'admin.settings.get':
            return done(storedSettings(state, args.file))
        case 'admin.settings.save':
            return done(saveSettings(state, args))
        case 'admin.settings.icon': {
            const stored = storedSettings(state, args.file)
            if (args.base !== stored.revision) return done(settingsAnswer('newer', stored.revision))
            stored.settings.find(entry => entry.key === 'dashboard.branding.icon').value = 'shared:icon-0123456789abcdef'
            stored.revision++
            return done(settingsAnswer('saved', stored.revision))
        }
        case 'admin.setup.get':
            return done(setupOf(state))
        case 'admin.setup.mark':
            setupOf(state).progress[args.page] = args.state
            return done(setupOf(state))
        case 'admin.settings.rollback': {
            const stored = storedSettings(state, args.file)
            if (args.base !== stored.revision) return done(settingsAnswer('newer', stored.revision))
            stored.revision++
            return done(settingsAnswer('saved', stored.revision))
        }
        case 'admin.settings.history':
            return done({revisions: args.page > 1 ? [] : SETTINGS_HISTORY, pageSize: 20})
        case 'admin.settings.revision':
            if (args.revision === 1) return done({...SETTINGS_HISTORY[1], file: args.file, first: true, changes: []})
            if (args.revision !== 2) return refused('This file has no such revision.')
            return done({...SETTINGS_HISTORY[0], file: args.file, first: false, changes: [
                {key: 'discord.enabled', before: false, after: true, secret: false, fileOnly: false},
                {key: 'discord.url', before: null, after: null, secret: true, fileOnly: false},
                {key: 'boards.presets', before: null, after: null, secret: false, fileOnly: true},
            ]})
        case 'company.create':
            if (args.name === 'Taken') return refused('A company with this name already exists.')
            return done(args.quote ? {name: args.name, price: 5000} : {company: 8, price: 5000})
        case 'company.disband':
            if (String(args.name).toLowerCase() !== 'stonebridge builders') return refused('The name does not match your company.')
            return done({company: 7})
        case 'company.members.transfer':
            return done({player: args.player})
        case 'orders.deliver.all':
            return done({order: args.order, delivered: 18, done: false})
        case 'orders.deliver.line':
            return done({order: args.order, delivered: 0, done: false})
        case 'orders.deliver.receive':
            return done({order: args.order, amount: 120, waiting: 20})
        case 'orders.deliver.collect':
            return done({received: 12, remaining: 0})
        case 'orders.create.catalogue':
            return done(CATALOGUE)
        case 'orders.create.items':
            return done(KNOWN_ITEMS)
        case 'orders.create.quote':
        case 'orders.create': {
            const quote = quoteOf(args)
            if (quote.refusal) return refused(quote.refusal)
            return done(name === 'orders.create' ? {order: 99, ...quote} : quote)
        }
        case 'orders.accept.abort':
            return done({order: args.order})
        case 'orders.own.cancel':
            return done(args.quote ? {order: args.order, repeating: false, charged: true} : {order: args.order, charged: true})
        case 'orders.own.extend':
            return done({order: args.order, left: 1, refundInSeconds: 86_400})
        case 'orders.own.raise':
            return done({order: args.order, increase: args.increase, fee: args.increase / 10, total: args.increase * 1.1})
        case 'inbox.read-all':
            return done({marked: 3})
        case 'inbox.clear':
            return done({deleted: 2})
        case 'inbox.mute':
            if (args.mute) state.muted.add(args.category)
            else state.muted.delete(args.category)
            return done({category: args.category, muted: args.mute, mask: state.muted.size})
        case 'company.members.kick':
            return done({player: args.player})
        case 'company.members.permissions':
            return done({player: args.player, permissions: Object.keys(args.permissions).filter(key => args.permissions[key])})
        case 'company.members.leave':
            return done({company: 7})
        case 'company.members.revoke':
            state.applications.invites = state.applications.invites.filter(invite => invite.player.uuid !== args.player)
            return done({player: args.player})
        case 'company.applications.accept':
        case 'company.applications.deny':
            state.applications.entries = state.applications.entries.filter(entry => entry.player.uuid !== args.player)
            return done({player: args.player})
        case 'company.applications.list':
            return page({total: state.applications.entries.length, ...state.applications})
        case 'company.applications.join-mode':
            return done({mode: args.mode})
        case 'company.profile.description':
        case 'company.profile.tag':
        case 'company.profile.color':
            if (args.tag !== undefined && args.tag.length > 5) return refused('The tag may have at most 5 characters.')
            return done({name: 'Stonebridge Builders', ...args})
        case 'company.profile.rename':
            if (args.quote) return done({price: 5000, treasury: 18450})
            return done({name: args.name})
        case 'company.treasury.deposit':
            if (args.amount > 10_000) return refused('You do not have that much money.')
            return done({balance: 18450 + args.amount})
        case 'company.treasury.withdraw':
            if (args.amount > 18450) return refused('The treasury does not hold that much.')
            return done({balance: 18450 - args.amount})
        case 'company.treasury.share':
            if (args.percent > 25) return refused('The share may be at most 25%.')
            return done({share: args.percent, balance: 18450})
        case 'orders.browse.search': {
            const marks = (story.features ?? FEATURES).includes('orders.servermarks')
            const events = (story.features ?? FEATURES).includes('events')
            const offer = {id: GOAL, name: 'Bridge logs', ends: new Date(Date.now() + 48 * 3_600_000).toISOString(), state: 'active'}
            const orders = BOOK
                .map(order => (marks && order.id === SERVER_ORDER ? {...order, server: true} : order))
                .map(order => (events && order.id === CHUNK_ORDER ? {...order, offer} : order))
                .filter(order => args.offers !== 'only' || order.offer)
                .filter(order => args.offer === undefined || order.offer?.id === args.offer)
                .filter(order => args.serverOrders !== 'only' || order.server)
                .filter(order => args.serverOrders !== 'hide' || !order.server)
                .filter(order => !args.name || order.name.toLowerCase().includes(args.name.toLowerCase()))
                .filter(order => args.maxOrderSize === undefined || order.lines[0].item.amount <= args.maxOrderSize)
                .filter(order => args.minPrice === undefined || order.lines[0].price >= args.minPrice)
            return page({total: BOOK.length, orders, canAccept: true, limitReached: false})
        }
        case 'orders.browse.details': {
            const found = BOOK.find(order => order.id === args.order)
            const events = (story.features ?? FEATURES).includes('events')
            const offer = {id: GOAL, name: 'Bridge logs', ends: new Date(Date.now() + 48 * 3_600_000).toISOString(), state: 'active'}
            return done(events && found?.id === CHUNK_ORDER ? {...found, offer} : found)
        }
        case 'market.top':
            return page({items: MARKET})
        case 'market.movers':
            return page({items: MARKET.filter(entry => entry.change !== undefined).sort((a, b) => Math.abs(b.change) - Math.abs(a.change))})
        case 'market.item': {
            const entry = MARKET.find(candidate => candidate.item.material === args.material)
            if (!entry) return {type: 'action.result', v: 1, ok: false, reason: 'dashboard.invalid', message: 'Unknown item'}
            return done({...entry, days: args.days, to: '2026-10-24', history: history(entry, args.days)})
        }
        case 'market.search':
            return page({items: MARKET.filter(entry => entry.item.name.toLowerCase().includes(String(args.query).toLowerCase()))})
        case 'inbox.page':
            return page({
                entries: INBOX.filter(entry => !args.query || entry.text.toLowerCase().includes(args.query.toLowerCase())),
                muted: [...state.muted],
                mutable: ['MEMBERS', 'ORDERS', 'PAYMENTS', 'LEVEL', 'ALERTS'],
            })
        case 'rankings.page':
            return page({...snapshot('').rankings, ranking: args.ranking, range: args.range})
        case 'company.treasury.log':
            return page({entries: TREASURY_LOG})
        default:
            return {type: 'action.result', v: 1, ok: false, reason: 'unknown-action', message: 'Unknown action'}
    }
}

/**
 * What the plugin's two nodes push for one delivery and a deposit, with their numbers running apart as they do when
 * a node serves many sessions: Kestrel_'s statistics and the order's progress from the first node, the deposit from
 * the second.
 *
 * @param session the session id
 * @returns the patches, in the order they are sent
 */
function delivery(session) {
    const stalls = positions()[2]
    stalls.lines[0].delivered += 64
    const kestrel = member(1, 3240 + 64)
    const base = {type: 'patch', v: 1, sessions: [session]}
    return [
        {...base, node: NODE, seq: 44, path: 'company.members', op: 'UPSERT', key: kestrel.uuid, value: kestrel},
        {...base, node: OTHER_NODE, seq: 7, path: 'company.treasury', op: 'REPLACE', value: {balance: 19600}},
        {...base, node: NODE, seq: 41, path: 'player.unreadInbox', op: 'REPLACE', value: 40},
        {...base, node: NODE, seq: 48, path: 'company.orders', op: 'UPSERT', key: String(stalls.id), value: stalls},
    ]
}

/**
 * Plays a session's story on its stream.
 *
 * @param client the browser's socket
 * @param session the session
 */
function play(client, session) {
    const send = message => client.readyState === client.OPEN && client.send(JSON.stringify(message))
    send({type: 'server', server: serverInfo(session)})
    const later = (millis, step) => setTimeout(step, millis)
    const timers = []
    if (session.story.stream === 'deliver') {
        timers.push(later(800, () => {
            for (const patch of delivery(session.id)) send(patch)
            session.state.delivered = true
        }))
        timers.push(later(1600, () => send({type: 'resync'})))
    } else if (session.story.stream === 'kick') {
        timers.push(later(800, () => {
            send({type: 'session.closed', v: 1, session: session.id, player: member(0).uuid, reason: 'REVOKED'})
            client.close(4401, 'Session ended')
        }))
    } else if (session.story.stream === 'discontinue') {
        timers.push(later(800, () => {
            session.state.discontinued = true
            client.close(4403, 'Discontinued')
        }))
    }
    client.on('close', () => timers.forEach(clearTimeout))
}

const sockets = new WebSocketServer({noServer: true, maxPayload: 8 * 1024 * 1024})

server.on('upgrade', (request, socket, head) => {
    if (request.url?.startsWith('/api/v1/servers/connect')) {
        sockets.handleUpgrade(request, socket, head, client => node(client, request))
        return
    }
    const session = sessionOf(request)
    if (!request.url?.startsWith('/api/v1/me/stream')) {
        socket.destroy()
        return
    }
    sockets.handleUpgrade(request, socket, head, client => {
        if (request.headers.origin && foreign(request)) {
            client.close(1008, 'Foreign origin')
            return
        }
        if (!session) {
            client.close(4401, 'Not logged in')
            return
        }
        play(client, session)
        client.on('message', text => {
            const message = JSON.parse(String(text))
            if (message.type === 'ping') client.send(JSON.stringify({type: 'pong', v: 1, time: message.time}))
        })
    })
})

/**
 * A node's connection: every message is answered with its size and the address the relay named, and `bye` is
 * answered by closing with 4409, as the gateway closes a replaced node.
 *
 * @param client the node's socket
 * @param request its upgrade request
 */
function node(client, request) {
    client.on('message', text => {
        const message = String(text)
        if (message === 'bye') {
            client.close(4409, 'Replaced by a newer connection')
            return
        }
        client.send(JSON.stringify({type: 'echo', size: message.length, forwardedFor: request.headers['x-forwarded-for'] ?? null, cookie: request.headers.cookie ?? null}))
    })
}

server.listen(PORT, () => console.info(`Mock gateway on http://localhost:${PORT}`))
