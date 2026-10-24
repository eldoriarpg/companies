/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {FakeSocket} from '~/test/fakes'
import {openStream, parseStreamMessage, PING_MILLIS, streamAddress, type StreamHandlers} from './stream'

/**
 * Handlers recording what the stream tells them.
 *
 * @returns the handlers and their record
 */
function recording() {
    const seen: string[] = []
    const handlers: StreamHandlers = {
        opened: () => seen.push('opened'),
        message: message => seen.push(message.type),
        closed: code => seen.push(`closed ${code}`),
    }
    return {seen, handlers}
}

/**
 * Opens a stream on a fake socket.
 *
 * @param handlers what is told
 * @returns the stream and its socket
 */
function open(handlers: StreamHandlers) {
    let socket: FakeSocket | null = null
    const stream = openStream(handlers, url => {
        socket = new FakeSocket(url)
        return socket
    })
    return {stream, socket: socket as unknown as FakeSocket}
}

beforeEach(() => vi.useFakeTimers())
afterEach(() => vi.useRealTimers())

/** The browser's end of the live stream. */
describe('the stream', () => {
    it('connects to the stream on the page\'s own host', () => {
        expect(streamAddress({protocol: 'https:', host: 'dash.example.org'})).toBe('wss://dash.example.org/api/v1/me/stream')
        expect(streamAddress({protocol: 'http:', host: 'localhost:4000'})).toBe('ws://localhost:4000/api/v1/me/stream')
    })

    it('reads messages and ignores what is none', () => {
        expect(parseStreamMessage('{"type":"resync"}')).toEqual({type: 'resync'})
        expect(parseStreamMessage('not json')).toBeNull()
        expect(parseStreamMessage('{"no":"type"}')).toBeNull()
    })

    it('reports opening, messages and the close code, and pings while open', () => {
        const {seen, handlers} = recording()
        const {socket} = open(handlers)
        socket.emit('open')
        socket.receive({type: 'resync'})
        socket.emit('message', {data: 'garbage'})
        vi.advanceTimersByTime(PING_MILLIS)
        expect(JSON.parse(socket.sent[0] as string)).toMatchObject({type: 'ping', v: 1})
        socket.emit('close', {code: 4401})
        vi.advanceTimersByTime(PING_MILLIS * 3)
        expect(socket.sent).toHaveLength(1)
        expect(seen).toEqual(['opened', 'resync', 'closed 4401'])
    })

    it('reports nothing once it was closed from this side', () => {
        const {seen, handlers} = recording()
        const {stream, socket} = open(handlers)
        socket.emit('open')
        stream.close()
        socket.receive({type: 'resync'})
        socket.emit('close', {code: 1006})
        expect(socket.closedWith).toBe(1000)
        expect(seen).toEqual(['opened'])
    })
})
