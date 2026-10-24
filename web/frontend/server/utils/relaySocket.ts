/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {Hooks, Peer} from 'crossws'
import {useRuntimeConfig} from '#imports'
import {createRelay} from './relay'
import {upstreamAddress, upstreamHeaders} from './socketAddress'
import {isTrusted, parseRanges} from './trustedProxies'

/** The routes the frontend relays, each with what it hands on. */
export interface RelayRoute {
    /** Whether the session cookie goes along. */
    cookie: boolean
    /** The largest message a peer may send. */
    maxMessageBytes: number
}

/**
 * The hooks of a WebSocket route relayed to the same path at the gateway, at the address and with the trusted proxies
 * the runtime config holds, both read per connection like the HTTP proxy reads them per request.
 *
 * @param route what the route hands on
 * @returns the hooks for `defineWebSocketHandler`
 */
export function relaySocket(route: RelayRoute): Partial<Hooks> {
    return createRelay({
        maxMessageBytes: route.maxMessageBytes,
        target(peer: Peer) {
            const {backendUrl, trustedProxies} = useRuntimeConfig()
            const trustedPeer = isTrusted(peer.remoteAddress, parseRanges(String(trustedProxies ?? '')))
            return {
                url: upstreamAddress(backendUrl, peer.request.url),
                headers: upstreamHeaders(peer.request.headers, peer.remoteAddress, {cookie: route.cookie, trustedPeer}),
            }
        },
    })
}
