/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {STREAM_PATH, type StreamMessage} from '~/api/gateway'

/** How often the browser pings, below the 100 seconds after which Cloudflare drops a quiet socket. */
export const PING_MILLIS = 20_000

/** What the stream reports to whoever opened it. */
export interface StreamHandlers {
    opened(): void
    message(message: StreamMessage): void
    closed(code: number, reason: string): void
}

/** An open stream. */
export interface DashboardStream {
    close(): void
}

/** The part of a WebSocket the stream uses, so a test can hand in its own. */
export interface StreamSocket {
    addEventListener(type: 'open', listener: () => void): void
    addEventListener(type: 'message', listener: (event: {data: unknown}) => void): void
    addEventListener(type: 'close', listener: (event: {code: number, reason: string}) => void): void
    send(text: string): void
    close(code?: number): void
}

/** Opens a socket to an address. */
export type SocketFactory = (url: string) => StreamSocket

/**
 * The stream's address on the page's own host, `wss:` behind `https:`.
 *
 * @param location the page's location
 * @returns the address
 */
export function streamAddress(location: Pick<Location, 'protocol' | 'host'>): string {
    return `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}${STREAM_PATH}`
}

/**
 * A stream message, or nothing for text that is not one.
 *
 * @param text what arrived
 * @returns the message
 */
export function parseStreamMessage(text: string): StreamMessage | null {
    try {
        const parsed = JSON.parse(text) as {type?: unknown}
        return typeof parsed?.type === 'string' ? parsed as StreamMessage : null
    } catch {
        return null
    }
}

/**
 * Opens the live stream of the session the cookie belongs to and pings it while it stands. Reconnecting is the
 * caller's, which knows whether the session still exists.
 *
 * <p>Closing the stream from this side detaches it first: nothing it does afterwards, its close event included,
 * reaches the handlers, so a caller replacing a stream never hears the old one end.
 *
 * @param handlers what is told about the stream
 * @param connect opens the socket, for tests
 * @returns the stream
 */
export function openStream(handlers: StreamHandlers, connect: SocketFactory = url => new WebSocket(url)): DashboardStream {
    const socket = connect(streamAddress(window.location))
    let ping: ReturnType<typeof setInterval> | undefined
    let detached = false
    socket.addEventListener('open', () => {
        if (detached) return
        handlers.opened()
        ping = setInterval(() => socket.send(JSON.stringify({type: 'ping', v: 1, time: new Date().toISOString()})), PING_MILLIS)
    })
    socket.addEventListener('message', event => {
        const message = !detached && typeof event.data === 'string' ? parseStreamMessage(event.data) : null
        if (message) handlers.message(message)
    })
    socket.addEventListener('close', event => {
        clearInterval(ping)
        if (!detached) handlers.closed(event.code, event.reason)
        detached = true
    })
    return {
        close(): void {
            detached = true
            clearInterval(ping)
            socket.close(1000)
        },
    }
}
