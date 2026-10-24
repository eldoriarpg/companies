/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {vi} from 'vitest'
import type {GatewayApi, ServerInfo} from '~/api/gateway'
import type {StreamSocket} from '~/api/stream'
import {snapshot} from './snapshots'

type Listener = (event: {data: unknown, code: number, reason: string}) => void

/**
 * A WebSocket a test drives by hand: it records what is sent and how it was closed, and dispatches the events a test
 * hands it. Closing it dispatches its close event at once, as an impatient browser would.
 */
export class FakeSocket implements StreamSocket {
    readonly sent: string[] = []
    closedWith: number | null = null
    private readonly listeners = new Map<string, Listener[]>()

    /**
     * @param url the address it was opened to
     */
    constructor(readonly url: string) {}

    /**
     * Registers a listener.
     *
     * @param type the event
     * @param listener the listener
     */
    addEventListener(type: string, listener: (event: never) => void): void {
        this.listeners.set(type, [...this.listeners.get(type) ?? [], listener as Listener])
    }

    /**
     * Records a sent message.
     *
     * @param text the message
     */
    send(text: string): void {
        this.sent.push(text)
    }

    /**
     * Closes the socket from this side.
     *
     * @param code the close code
     */
    close(code = 1000): void {
        this.closedWith = code
        this.emit('close', {code, reason: ''})
    }

    /**
     * Dispatches an event.
     *
     * @param type the event
     * @param event its fields
     */
    emit(type: string, event: {data?: unknown, code?: number, reason?: string} = {}): void {
        for (const listener of this.listeners.get(type) ?? []) listener({data: undefined, code: 1005, reason: '', ...event})
    }

    /**
     * Dispatches a message as the gateway sends it.
     *
     * @param message the message, as an object
     */
    receive(message: object): void {
        this.emit('message', {data: JSON.stringify(message)})
    }
}

/**
 * The server of the tests, online with every feature the dashboard reads.
 *
 * @param change what differs
 * @returns the server
 */
export function serverInfo(change: Partial<ServerInfo> = {}): ServerInfo {
    return {
        server: {name: 'Mossvale SMP'},
        features: ['orders.browse', 'market', 'inbox', 'rankings'],
        limits: {maxPageSize: 50},
        version: {status: 'SUPPORTED'},
        deprecated: [],
        online: true,
        ...change,
    }
}

/**
 * Gateway calls answering with the test server and snapshot; each can be replaced per test.
 *
 * @returns the calls as mocks
 */
export function fakeApi() {
    return {
        pending: vi.fn<GatewayApi['pending']>(),
        login: vi.fn<GatewayApi['login']>(),
        server: vi.fn<GatewayApi['server']>(async () => serverInfo()),
        snapshot: vi.fn<GatewayApi['snapshot']>(async () => snapshot()),
        read: vi.fn() as unknown as GatewayApi['read'],
        logout: vi.fn<GatewayApi['logout']>(async () => undefined),
    }
}

/**
 * An axios failure as the gateway answers it.
 *
 * @param status the HTTP status
 * @param body the error body
 * @returns the error
 */
export function gatewayError(status: number, body: object): Error {
    return Object.assign(new Error(`HTTP ${status}`), {isAxiosError: true, response: {status, data: body}})
}
