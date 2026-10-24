/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
/** @vitest-environment node */
import type {Message, Peer} from 'crossws'
import {describe, expect, it} from 'vitest'
import {createRelay, type Upstream} from './relay'

type Listener = (event: {data?: unknown, code?: number, reason?: string}) => void

/** A backend connection a test drives by hand. */
class FakeUpstream implements Upstream {
    readyState = 0
    binaryType = 'blob'
    readonly sent: string[] = []
    closedWith: {code?: number, reason?: string} | null = null
    readonly listeners = new Map<string, Listener[]>()

    /**
     * @param url where it connects
     * @param headers the headers it connects with
     */
    constructor(readonly url: string, readonly headers: Record<string, string>) {}

    /**
     * Registers a listener.
     *
     * @param type the event
     * @param listener the listener
     */
    addEventListener(type: string, listener: Listener): void {
        this.listeners.set(type, [...this.listeners.get(type) ?? [], listener])
    }

    /**
     * Records a sent message.
     *
     * @param data the message
     */
    send(data: string): void {
        this.sent.push(data)
    }

    /**
     * Records the close.
     *
     * @param code the code
     * @param reason the reason
     */
    close(code?: number, reason?: string): void {
        this.closedWith = {code, reason}
    }

    /**
     * Dispatches an event.
     *
     * @param type the event
     * @param event its fields
     */
    emit(type: string, event: {data?: unknown, code?: number, reason?: string} = {}): void {
        if (type === 'open') this.readyState = 1
        for (const listener of this.listeners.get(type) ?? []) listener(event)
    }
}

/**
 * A relay with a fake backend and a fake peer.
 *
 * @param maxMessageBytes the largest message the peer may send
 * @returns the hooks, the peer's record and the backend
 */
function relay(maxMessageBytes = 1024) {
    const upstreams: FakeUpstream[] = []
    const hooks = createRelay({
        maxMessageBytes,
        target: () => ({url: 'ws://gateway/api/v1/servers/connect', headers: {'x-forwarded-for': '10.0.0.2'}}),
        connect: (url, headers) => {
            const upstream = new FakeUpstream(url, headers)
            upstreams.push(upstream)
            return upstream
        },
    })
    const peer = {sent: [] as unknown[], closed: null as {code?: number, reason?: string} | null}
    const fake = {
        send: (data: unknown) => peer.sent.push(data),
        close: (code?: number, reason?: string) => {
            peer.closed = {code, reason}
        },
    } as unknown as Peer
    hooks.open?.(fake)
    const message = (text: string) => hooks.message?.(fake, {rawData: text, text: () => text} as unknown as Message)
    return {hooks, peer, fake, message, upstream: upstreams[0] as FakeUpstream}
}

/** The relay between a peer and the gateway. */
describe('the relay', () => {
    it('holds messages back until the backend stands and passes them both ways', () => {
        const {peer, message, upstream} = relay()
        expect(upstream.headers['x-forwarded-for']).toBe('10.0.0.2')
        message('hello')
        expect(upstream.sent).toEqual([])
        upstream.emit('open')
        expect(upstream.sent).toEqual(['hello'])
        upstream.emit('message', {data: 'welcome'})
        expect(peer.sent).toEqual(['welcome'])
    })

    it('passes the gateway\'s close codes to the peer unchanged', () => {
        for (const code of [4401, 4403, 4409, 4429, 1008, 1001, 1011]) {
            const {peer, upstream} = relay()
            upstream.emit('close', {code, reason: 'why'})
            expect(peer.closed).toEqual({code, reason: 'why'})
        }
    })

    it('tells the peer to try again when the gateway vanished', () => {
        const {peer, upstream} = relay()
        upstream.emit('close', {code: 1006, reason: ''})
        expect(peer.closed?.code).toBe(1011)
        const other = relay()
        other.upstream.emit('error')
        expect(other.peer.closed?.code).toBe(1011)
    })

    it('passes the peer\'s close codes to the gateway as far as a client may send them', () => {
        const node = relay()
        node.upstream.emit('open')
        node.hooks.close?.(node.fake, {code: 4000, reason: 'Stopping'})
        expect(node.upstream.closedWith).toEqual({code: 4000, reason: 'Stopping'})
        const browser = relay()
        browser.upstream.emit('open')
        browser.hooks.close?.(browser.fake, {code: 1001, reason: 'Gone'})
        expect(browser.upstream.closedWith?.code).toBe(1000)
    })

    it('closes a peer that sends too much', () => {
        const {peer, message, upstream} = relay(8)
        upstream.emit('open')
        message('0123456789')
        expect(peer.closed?.code).toBe(1009)
        expect(upstream.sent).toEqual([])
        expect(upstream.closedWith?.code).toBe(1000)
    })
})
