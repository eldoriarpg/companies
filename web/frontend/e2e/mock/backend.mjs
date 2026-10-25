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
            return {status: 200, body: action(body.name, body.args ?? {}, state)}
        case 'POST /api/v1/me/logout':
            return {status: 204, headers: {'set-cookie': `${COOKIE}=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict`}}
        default:
            return error(404, 'UNKNOWN_SESSION')
    }
}

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
        ? [...(session.story.features ?? FEATURES), 'admin.orders', 'admin.players', 'admin.companies', 'admin.levels', 'admin.boards', 'admin.nodes', 'admin.config']
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

/**
 * The servers in the database of a session, made on first use: both nodes and an old one.
 *
 * @param state the session's state
 * @returns the servers
 */
function servers(state) {
    state.servers ??= [
        {uid: PRIMARY_NODE, role: 'PRIMARY', version: '2.1.0', lastSeen: '2026-10-25T08:00:00Z', bus: 'Redis', current: true},
        {uid: SECONDARY_NODE, role: 'SECONDARY', version: '2.1.0', lastSeen: '2026-10-25T08:01:00Z', bus: 'Redis', current: false},
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
    return {level, name, orderCount: level * 10, memberCount: level, earnedMoney: level * 1000, deliveredItems: level * 500, maxMembers: 5 + level * 5, maxOrders: level + 2, maxOrderSize: 0, deliveryHoursBonus: 0, taxPercent: null, maxAlerts: null}
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
 * The answer to an action. Accepting order 3 is refused as the plugin words it; every other action succeeds.
 *
 * @param name the action
 * @param args its arguments
 * @param state the session's state, which keeps the muted inbox categories
 * @returns the action result
 */
function action(name, args, state) {
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
            return done({order: args.order, refunded: args.refund ? 1155 : 0, collected: args.refund ? 0 : 1155})
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
        case 'admin.nodes.remove':
            state.servers = servers(state).filter(entry => entry.uid !== args.node)
            return done({nodes: state.servers})
        case 'admin.config.sync':
            return done({})
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
            return done(args.quote ? {order: args.order, repeating: false} : {order: args.order})
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
            const orders = BOOK
                .filter(order => !args.name || order.name.toLowerCase().includes(args.name.toLowerCase()))
                .filter(order => args.maxOrderSize === undefined || order.lines[0].item.amount <= args.maxOrderSize)
                .filter(order => args.minPrice === undefined || order.lines[0].price >= args.minPrice)
            return page({total: BOOK.length, orders, canAccept: true, limitReached: false})
        }
        case 'orders.browse.details':
            return {type: 'action.result', v: 1, ok: true, data: BOOK.find(order => order.id === args.order)}
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
