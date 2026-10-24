/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {randomUUID} from 'node:crypto'
import {createServer} from 'node:http'
import {WebSocketServer} from 'ws'
import {
    BOOK, CODE, DISCONTINUED, FEATURES, INBOX, MARKET, member, NODE, OTHER_NODE, positions, SESSIONS, snapshot, TREASURY_LOG,
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
            return {status: 200, body: snapshot(session.id, state.delivered)}
        case 'POST /api/v1/me/actions':
            if (story.offline) return error(504, 'SERVER_OFFLINE', {message: 'The server did not answer in time'})
            return {status: 200, body: action(body.name, body.args ?? {})}
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
    return {server: {name: 'Mossvale SMP'}, features: session.story.features ?? FEATURES, limits: {maxPageSize: 50}, version, deprecated: [], online: !session.story.offline}
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
 * The answer to a read action.
 *
 * @param name the action
 * @param args its arguments
 * @returns the action result
 */
function action(name, args) {
    const page = data => ({type: 'action.result', v: 1, ok: true, data: {page: args.page ?? 1, pages: 1, ...data}})
    switch (name) {
        case 'orders.browse.search': {
            const orders = BOOK
                .filter(order => !args.name || order.name.toLowerCase().includes(args.name.toLowerCase()))
                .filter(order => args.maxOrderSize === undefined || order.lines[0].item.amount <= args.maxOrderSize)
                .filter(order => args.minPrice === undefined || order.lines[0].price >= args.minPrice)
            return page({total: BOOK.length, orders})
        }
        case 'orders.browse.details':
            return {type: 'action.result', v: 1, ok: true, data: BOOK.find(order => order.id === args.order)}
        case 'market.top':
            return page({items: MARKET})
        case 'market.search':
            return page({items: MARKET.filter(entry => entry.item.name.toLowerCase().includes(String(args.query).toLowerCase()))})
        case 'inbox.page':
            return page({entries: INBOX.filter(entry => !args.query || entry.text.toLowerCase().includes(args.query.toLowerCase()))})
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
