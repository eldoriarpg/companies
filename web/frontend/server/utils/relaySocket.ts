/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import type {Hooks, Message, Peer} from 'crossws'
import {useRuntimeConfig} from '#imports'
import {NORMAL_CLOSURE, relayableCloseCode, upstreamAddress, upstreamHeaders} from './socketAddress'

/** What a relay keeps in the context of the browser's peer. */
interface Relay {
    upstream: WebSocket
    pending: (string | ArrayBuffer)[]
}

/**
 * The relay stored for a peer, if it has one.
 *
 * @param peer the browser's peer
 * @returns the relay
 */
function relayOf(peer: Peer): Relay | undefined {
    return peer.context.relay as Relay | undefined
}

/**
 * Opens the backend connection for a browser peer and wires the backend's side of it: messages go
 * to the browser, a close reaches the browser with the backend's code and reason. The browser's
 * session cookie, origin and address go along, since the backend knows the session by its cookie.
 *
 * @param peer the browser's peer
 */
function connect(peer: Peer): void {
    const {backendUrl} = useRuntimeConfig()
    const headers = upstreamHeaders(peer.request.headers, peer.remoteAddress)
    const upstream = new WebSocket(upstreamAddress(backendUrl, peer.request.url), {headers} as unknown as string[])
    upstream.binaryType = 'arraybuffer'
    const relay: Relay = {upstream, pending: []}
    peer.context.relay = relay

    upstream.addEventListener('open', () => {
        for (const message of relay.pending) upstream.send(message)
        relay.pending = []
    })
    upstream.addEventListener('message', event => peer.send(event.data))
    upstream.addEventListener('close', event => peer.close(relayableCloseCode(event.code), event.reason))
    upstream.addEventListener('error', () => peer.close(NORMAL_CLOSURE, 'Backend unavailable'))
}

/**
 * Hands a browser message to the backend, held back until the backend connection stands.
 *
 * @param peer the browser's peer
 * @param message what the browser sent
 */
function forward(peer: Peer, message: Message): void {
    const relay = relayOf(peer)
    if (!relay) return
    const data = typeof message.rawData === 'string' ? message.rawData : message.text()
    if (relay.upstream.readyState === WebSocket.OPEN) relay.upstream.send(data)
    else if (relay.upstream.readyState === WebSocket.CONNECTING) relay.pending.push(data)
}

/**
 * Closes the backend connection once the browser has gone.
 *
 * @param peer the browser's peer
 * @param details the code and reason the browser closed with
 */
function disconnect(peer: Peer, details: {code?: number, reason?: string}): void {
    const relay = relayOf(peer)
    if (!relay) return
    delete peer.context.relay
    const state = relay.upstream.readyState
    if (state === WebSocket.OPEN || state === WebSocket.CONNECTING) {
        relay.upstream.close(relayableCloseCode(details.code), details.reason)
    }
}

/**
 * The hooks of a WebSocket route that relays every message both ways between the browser and the
 * same path at the backend.
 *
 * <p>Nitro's HTTP proxy cannot carry an upgrade, so a WebSocket the backend serves is answered here
 * instead: the browser connects to this server, which opens the matching connection to the backend
 * and passes text through unchanged in both directions, query string included. A close on either
 * side closes the other with the same code where a client may send that code. The backend address
 * is read from the runtime config per connection, like the HTTP proxy reads it per request.
 *
 * @returns the hooks for `defineWebSocketHandler`
 */
export function relaySocket(): Partial<Hooks> {
    return {
        open: connect,
        message: forward,
        close: disconnect,
        error: peer => disconnect(peer, {}),
    }
}
