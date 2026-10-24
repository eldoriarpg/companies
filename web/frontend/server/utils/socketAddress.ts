/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */

/** The close code sent on when the other side gave one a WebSocket client may not send itself. */
export const NORMAL_CLOSURE = 1000

/** The close code sent to the browser when the gateway went away without a code it could pass on. */
export const UNAVAILABLE = 1011

/** The header Cloudflare names the client's address in, believed only from a trusted proxy. */
export const CLIENT_ADDRESS_HEADER = 'cf-connecting-ip'

/**
 * The WebSocket address of the backend for the path and query of an upgrade request, appended to the
 * backend address the way the HTTP proxy appends a path.
 *
 * @param backendUrl the backend's HTTP address
 * @param requestUrl the address the browser asked for, absolute or not
 * @returns the same path and query at the backend, as a `ws:` or `wss:` address
 */
export function upstreamAddress(backendUrl: string, requestUrl: string): string {
    const requested = new URL(requestUrl, 'http://localhost')
    const target = new URL(`${backendUrl.replace(/\/$/, '')}${requested.pathname}${requested.search}`)
    target.protocol = target.protocol === 'https:' ? 'wss:' : 'ws:'
    return target.toString()
}

/**
 * A close code the relay may pass on through a WebSocket client: 1000 and the range 3000 to 4999.
 * Anything else, a missing code included, is sent on as a normal closure.
 *
 * @param code the code the other side closed with
 * @returns the code to close with
 */
export function relayableCloseCode(code: number | undefined): number {
    if (code === NORMAL_CLOSURE) return code
    if (code !== undefined && code >= 3000 && code <= 4999) return code
    return NORMAL_CLOSURE
}

/**
 * A close code the relay may pass on as the server side of a WebSocket: every code but those no endpoint may send
 * (1004 to 1006, 1015) and those outside the defined ranges, which become the given fallback. A server may send
 * `1001` going away and `1011` failed, which a client may not.
 *
 * @param code the code the other side closed with
 * @param fallback what a code that cannot be sent becomes
 * @returns the code to close with
 */
export function sendableCloseCode(code: number | undefined, fallback: number): number {
    if (code === undefined) return fallback
    if (code >= 3000 && code <= 4999) return code
    if (code >= 1000 && code <= 1014 && ![1004, 1005, 1006].includes(code)) return code
    return fallback
}

/** What the relay hands on to the backend besides the addresses. */
export interface ForwardOptions {
    /** Whether the session cookie goes along, which only the browser stream needs. */
    cookie: boolean
    /** Whether the connection came from a trusted proxy, whose `CF-Connecting-IP` is believed. */
    trustedPeer: boolean
}

/**
 * The headers of the backend connection: the browser's origin and user agent, its cookie where the route needs it,
 * Cloudflare's client address only from a trusted proxy (anyone else could set it to any address), and
 * `X-Forwarded-For` with the address the connection came from appended, as the HTTP proxy does.
 *
 * @param request the headers of the upgrade request
 * @param remote the address the connection came from
 * @param options what goes along
 * @returns the headers to connect with
 */
export function upstreamHeaders(request: Headers, remote: string | undefined, options: ForwardOptions): Record<string, string> {
    const names = ['origin', 'user-agent', ...(options.cookie ? ['cookie'] : []), ...(options.trustedPeer ? [CLIENT_ADDRESS_HEADER] : [])]
    const headers: Record<string, string> = {}
    for (const name of names) {
        const value = request.get(name)
        if (value) headers[name] = value
    }
    const forwarded = [request.get('x-forwarded-for'), remote].filter(Boolean).join(', ')
    if (forwarded) headers['x-forwarded-for'] = forwarded
    return headers
}
