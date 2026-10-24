/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {defineEventHandler, getRequestHeader, getRequestIP, proxyRequest} from 'h3'
import {useRuntimeConfig} from '#imports'

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
 * route that declares a WebSocket handler for the path, which is where the dashboard stream is relayed
 * (`server/routes/api/v1/me/stream.ts`).
 *
 * <p>The address the request came from is appended to `X-Forwarded-For`, as any proxy does, so the
 * backend can tell callers apart (it trusts this server through `TRUSTED_PROXIES`).
 */
export default defineEventHandler(event => {
    if (!belongsToBackend(event.path)) return

    const {backendUrl} = useRuntimeConfig(event)
    return proxyRequest(event, `${backendUrl}${event.path}`, {headers: {'x-forwarded-for': forwardedFor(event)}})
})
