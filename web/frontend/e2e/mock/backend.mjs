/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {createServer} from 'node:http'
import {WebSocketServer} from 'ws'
import {BOOK, CODE, FEATURES, INBOX, MARKET, member, NODE, positions, SESSIONS, snapshot, TREASURY_LOG} from './data.mjs'

/** Where the mock listens, unless `MOCK_PORT` says otherwise. */
const PORT = Number(process.env.MOCK_PORT ?? 8787)

const COOKIE = 'companies_session'

/**
 * A stand-in for the gateway's browser API, for the end-to-end stories: the contract of `src/api/gateway.ts` with fixed
 * data. Every waiting session of `SESSIONS` takes the code `CODE`; the cookie it sets names the short id, so each
 * story's session keeps the server state it logged in to. A stream sends two patches shortly after it opens, the two
 * halves of one delivery, so the live feed has something to show.
 */
const server = createServer(async (request, response) => {
    const url = new URL(request.url ?? '/', 'http://mock')
    const body = request.method === 'POST' ? await readBody(request) : {}
    const answer = route(request.method ?? 'GET', url.pathname, body, sessionOf(request))
    response.writeHead(answer.status, {'content-type': 'application/json', 'cache-control': 'no-store', ...answer.headers})
    response.end(answer.body === undefined ? '' : JSON.stringify(answer.body))
})

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
 * The short id the session cookie names.
 *
 * @param request the request
 * @returns the short id, or nothing without a session
 */
function sessionOf(request) {
    const cookie = request.headers.cookie ?? ''
    const value = cookie.split(';').map(part => part.trim().split('=')).find(([name]) => name === COOKIE)?.[1]
    return value && SESSIONS[value] ? value : null
}

/**
 * The version state of a mock server.
 *
 * @param shortId the short id of its session
 * @returns the state
 */
function version(shortId) {
    return SESSIONS[shortId]
}

/**
 * Answers one request.
 *
 * @param method the method
 * @param path the path
 * @param body the JSON body
 * @param session the short id of the cookie's session
 * @returns status, headers and body
 */
function route(method, path, body, session) {
    const pending = path.match(/^\/api\/v1\/s\/([^/]+)(\/login)?$/)
    if (pending) return login(method, decodeURIComponent(pending[1]), Boolean(pending[2]), body)
    if (!path.startsWith('/api/v1/me/')) return {status: 404, body: {error: 'UNKNOWN_SESSION'}}
    if (!session) return {status: 401, body: {error: 'UNAUTHORIZED'}}
    const branding = {name: 'Mossvale SMP'}
    switch (`${method} ${path}`) {
        case 'GET /api/v1/me/server':
            return {status: 200, body: {server: branding, features: FEATURES, limits: {maxPageSize: 50}, version: version(session), deprecated: []}}
        case 'GET /api/v1/me/snapshot':
            return {status: 200, body: snapshot(session)}
        case 'POST /api/v1/me/actions':
            return {status: 200, body: action(body.name, body.args ?? {})}
        case 'POST /api/v1/me/logout':
            return {status: 204, headers: {'set-cookie': `${COOKIE}=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict`}}
        default:
            return {status: 404, body: {error: 'UNKNOWN_SESSION'}}
    }
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
    if (!SESSIONS[shortId]) return {status: 404, body: {error: 'UNKNOWN_SESSION'}}
    if (!submit && method === 'GET') {
        const expiresAt = new Date(Date.now() + 5 * 60_000).toISOString()
        return {status: 200, body: {server: {name: 'Mossvale SMP'}, expiresAt, attemptsLeft: 5, version: version(shortId)}}
    }
    if (body.code !== CODE) return {status: 401, body: {error: 'WRONG_CODE', attemptsLeft: 4}}
    return {status: 204, headers: {'set-cookie': `${COOKIE}=${shortId}; Path=/; HttpOnly; SameSite=Strict`}}
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
 * The two halves of one delivery as the plugin pushes them: Kestrel_'s statistics, then the order's progress.
 *
 * @param session the session id
 * @returns the patches
 */
function delivery(session) {
    const stalls = positions()[2]
    stalls.lines[0].delivered += 64
    const kestrel = member(1, 3240 + 64)
    const base = {type: 'patch', v: 1, sessions: [session], node: NODE}
    return [
        {...base, seq: 42, path: 'company.members', op: 'UPSERT', key: kestrel.uuid, value: kestrel},
        {...base, seq: 43, path: 'company.orders', op: 'UPSERT', key: String(stalls.id), value: stalls},
    ]
}

const sockets = new WebSocketServer({noServer: true})

server.on('upgrade', (request, socket, head) => {
    const session = sessionOf(request)
    if (!session || !request.url?.startsWith('/api/v1/me/stream')) {
        socket.destroy()
        return
    }
    sockets.handleUpgrade(request, socket, head, client => {
        const timer = setTimeout(() => {
            for (const patch of delivery(session)) client.send(JSON.stringify(patch))
        }, 800)
        client.on('message', text => {
            const message = JSON.parse(String(text))
            if (message.type === 'ping') client.send(JSON.stringify({type: 'pong', v: 1, time: message.time}))
        })
        client.on('close', () => clearTimeout(timer))
    })
})

server.listen(PORT, () => console.info(`Mock gateway on http://localhost:${PORT}`))
