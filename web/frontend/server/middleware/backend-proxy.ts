/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {defineEventHandler, getRequestHeader, getRequestIP, proxyRequest} from 'h3'
import {useRuntimeConfig} from '#imports'
import {CLIENT_ADDRESS_HEADER} from '../utils/socketAddress'
import {isTrusted, parseRanges} from '../utils/trustedProxies'

/**
 * Whether a path belongs to the backend.
 *
 * @param path the request path
 * @returns true for everything below `/api/`
 */
function belongsToBackend(path: string): boolean {
    return path.startsWith('/api/')
}

/**
 * @returns the forwarded addresses with the one this request came from appended
 */
function forwardedFor(event: Parameters<typeof getRequestIP>[0]): string {
    const before = getRequestHeader(event, 'x-forwarded-for')
    const remote = getRequestIP(event) ?? ''
    return before ? `${before}, ${remote}` : remote
}

/**
 * Hands the paths the backend owns straight to it, at the address this server holds while it runs.
 *
 * <p>The address is read per request, so one build serves any backend. Everything else falls
 * through untouched: returning nothing from a middleware lets the request carry on to the pages.
 *
 * <p>This covers HTTP only. A WebSocket upgrade never reaches a middleware: Nitro hands it to the
 * route that declares a WebSocket handler for the path, which is where the dashboard stream
 * (`server/routes/api/v1/me/stream.ts`) and the nodes' connection (`server/routes/api/v1/servers/connect.ts`)
 * are relayed.
 *
 * <p>The address the request came from is appended to `X-Forwarded-For`, as any proxy does, so the
 * backend can tell callers apart (it trusts this server through `TRUSTED_PROXIES`). Cloudflare's
 * `CF-Connecting-IP` is handed on only when the request came from a proxy named in `NUXT_TRUSTED_PROXIES`,
 * such as the tunnel; from anyone else it is dropped, since a browser could set it to any address.
 */
export default defineEventHandler(event => {
    if (!belongsToBackend(event.path)) return

    const {backendUrl, trustedProxies} = useRuntimeConfig(event)
    if (!isTrusted(getRequestIP(event), parseRanges(String(trustedProxies ?? '')))) {
        Reflect.deleteProperty(event.node.req.headers, CLIENT_ADDRESS_HEADER)
    }
    return proxyRequest(event, `${backendUrl}${event.path}`, {headers: {'x-forwarded-for': forwardedFor(event)}})
})
