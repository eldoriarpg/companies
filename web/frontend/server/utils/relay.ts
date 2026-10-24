/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {Hooks, Message, Peer} from 'crossws'
import {relayableCloseCode, sendableCloseCode, UNAVAILABLE} from './socketAddress'

/** The part of the backend connection the relay uses, so a test can hand in its own. */
export interface Upstream {
    readonly readyState: number
    binaryType: string
    send(data: string): void
    close(code?: number, reason?: string): void
    addEventListener(type: 'open' | 'message' | 'close' | 'error', listener: (event: {data?: unknown, code?: number, reason?: string}) => void): void
}

/** How one relay route behaves. */
export interface RelayOptions {
    /** The backend's address and the headers to connect with, for the peer that connected. */
    target(peer: Peer): {url: string, headers: Record<string, string>}
    /** The largest message a peer may send, in bytes of its text; a larger one closes the connection with 1009. */
    maxMessageBytes: number
    /** Opens the backend connection; the platform's WebSocket by default. */
    connect?(url: string, headers: Record<string, string>): Upstream
}

const OPEN = 1
const CONNECTING = 0
const MESSAGE_TOO_BIG = 1009

/** What a relay keeps in the context of the peer. */
interface Relay {
    upstream: Upstream
    pending: string[]
}

/**
 * Opens the platform's WebSocket with headers, which Node's WebSocket takes in its options.
 *
 * @param url the backend's address
 * @param headers the headers
 * @returns the connection
 */
function platformSocket(url: string, headers: Record<string, string>): Upstream {
    return new WebSocket(url, {headers} as unknown as string[]) as unknown as Upstream
}

/**
 * The hooks of a WebSocket route that relays every message both ways between the peer and the same path at the
 * backend.
 *
 * <p>Nitro's HTTP proxy cannot carry an upgrade, so a WebSocket the backend serves is answered here instead: the peer
 * connects to this server, which opens the matching connection to the backend and passes text through unchanged in
 * both directions. A close on either side closes the other with the same code: the backend's codes reach the peer as
 * they are, except the ones no endpoint may send, such as 1006 for a backend that vanished, which become 1011 so the
 * peer tries again. The relay is a client towards the backend, and a client may send only 1000 and 3000 to 4999, so
 * the peer's codes pass unchanged within those (the plugin's and the gateway's 44xx among them) and become 1000
 * otherwise.
 *
 * @param options how the route behaves
 * @returns the hooks for `defineWebSocketHandler`
 */
export function createRelay(options: RelayOptions): Partial<Hooks> {
    const connect = options.connect ?? platformSocket
    const relays = new WeakMap<Peer, Relay>()

    /**
     * Closes the connection to the backend once the peer has gone.
     *
     * @param peer the peer
     * @param code the code the peer closed with
     * @param reason its reason
     */
    function disconnect(peer: Peer, code: number | undefined, reason: string | undefined): void {
        const relay = relays.get(peer)
        if (!relay) return
        relays.delete(peer)
        if (relay.upstream.readyState !== OPEN && relay.upstream.readyState !== CONNECTING) return
        relay.upstream.close(relayableCloseCode(code), reason)
    }

    return {
        open(peer: Peer): void {
            const {url, headers} = options.target(peer)
            const upstream = connect(url, headers)
            upstream.binaryType = 'arraybuffer'
            const relay: Relay = {upstream, pending: []}
            relays.set(peer, relay)
            upstream.addEventListener('open', () => {
                for (const message of relay.pending) upstream.send(message)
                relay.pending = []
            })
            upstream.addEventListener('message', event => peer.send(event.data as string))
            upstream.addEventListener('close', event => {
                relays.delete(peer)
                peer.close(sendableCloseCode(event.code, UNAVAILABLE), event.reason)
            })
            upstream.addEventListener('error', () => {
                relays.delete(peer)
                peer.close(UNAVAILABLE, 'Backend unavailable')
            })
        },
        message(peer: Peer, message: Message): void {
            const relay = relays.get(peer)
            if (!relay) return
            const text = typeof message.rawData === 'string' ? message.rawData : message.text()
            if (Buffer.byteLength(text) > options.maxMessageBytes) {
                peer.close(MESSAGE_TOO_BIG, 'Message too big')
                disconnect(peer, MESSAGE_TOO_BIG, 'Message too big')
                return
            }
            if (relay.upstream.readyState === OPEN) relay.upstream.send(text)
            else if (relay.upstream.readyState === CONNECTING) relay.pending.push(text)
        },
        close(peer: Peer, details: {code?: number, reason?: string}): void {
            disconnect(peer, details.code, details.reason)
        },
        error(peer: Peer): void {
            disconnect(peer, undefined, undefined)
        },
    }
}
