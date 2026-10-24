/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import type {Snapshot} from '~/api/generated/protocol'
import {FakeSocket, fakeApi, gatewayError, serverInfo} from '~/test/fakes'
import {ALEX, member, NODE, patch, snapshot} from '~/test/snapshots'
import {serverToShow, useDashboard} from './useDashboard'

const OTHER = '9a8b7c6d-0000-4000-8000-000000000001'

/**
 * A dashboard wired to a fake gateway, with the sockets it opened.
 *
 * @param api the gateway's calls
 * @returns the dashboard and its sockets
 */
function wired(api = fakeApi()) {
    const sockets: FakeSocket[] = []
    const dashboard = useDashboard({api, connect: url => {
        const socket = new FakeSocket(url)
        sockets.push(socket)
        return socket
    }})
    return {api, sockets, dashboard, socket: () => sockets.at(-1) as FakeSocket}
}

/**
 * A promise to be settled by the test.
 *
 * @returns the promise and its resolve
 */
function deferred<T>() {
    let resolve: (value: T) => void = () => undefined
    const promise = new Promise<T>(done => {
        resolve = done
    })
    return {promise, resolve}
}

/** Lets pending promises settle. */
async function settle(): Promise<void> {
    await vi.advanceTimersByTimeAsync(0)
}

beforeEach(() => {
    vi.useFakeTimers()
    useDashboard().forget()
})

afterEach(() => {
    useDashboard().forget()
    vi.useRealTimers()
})

/** The dashboard's session: snapshot, patches, resyncs and the ends of the stream. */
describe('the dashboard session', () => {
    it('opens the stream before the snapshot and applies the patches that came meanwhile above its sequence', async () => {
        const later = deferred<Snapshot>()
        const {api, dashboard, socket} = wired()
        api.snapshot.mockReturnValueOnce(later.promise)
        dashboard.start()
        socket().emit('open')
        await settle()
        socket().receive(patch(40, {path: 'player.unreadInbox', op: 'REPLACE', value: 40}))
        socket().receive(patch(43, {path: 'player.unreadInbox', op: 'REPLACE', value: 43}))
        socket().receive(patch(7, {path: 'company.treasury', op: 'REPLACE', value: {balance: 7}, node: OTHER}))
        later.resolve(snapshot())
        await settle()
        expect(dashboard.status.value).toBe('ready')
        expect(dashboard.snapshot.value?.player.unreadInbox).toBe(43)
        expect(dashboard.company.value?.treasury?.balance).toBe(7)
    })

    it('applies interleaved numbers of several nodes and drops only what is not newer', async () => {
        const {dashboard, socket} = wired()
        dashboard.start()
        await settle()
        socket().receive(patch(42, {path: 'player.unreadInbox', op: 'REPLACE', value: 42}))
        socket().receive(patch(7, {path: 'company.tag', op: 'REPLACE', value: 'B7', node: OTHER}))
        socket().receive(patch(47, {path: 'player.unreadInbox', op: 'REPLACE', value: 47}))
        socket().receive(patch(44, {path: 'player.unreadInbox', op: 'REPLACE', value: 44}))
        socket().receive(patch(9, {path: 'company.tag', op: 'REPLACE', value: 'B9', node: OTHER}))
        expect(dashboard.snapshot.value?.player.unreadInbox).toBe(47)
        expect(dashboard.company.value?.tag).toBe('B9')
    })

    it('loads only the snapshot again on a resync, without opening another stream', async () => {
        const {api, dashboard, sockets, socket} = wired()
        dashboard.start()
        socket().emit('open')
        await settle()
        api.snapshot.mockResolvedValueOnce({...snapshot(), seq: 90, player: {...snapshot().player, unreadInbox: 9}})
        socket().receive({type: 'resync'})
        socket().receive({type: 'resync'})
        await settle()
        expect(sockets).toHaveLength(1)
        expect(api.snapshot).toHaveBeenCalledTimes(3)
        expect(socket().closedWith).toBeNull()
        expect(vi.getTimerCount()).toBe(1)
        expect(dashboard.live.value).toBe(true)
    })

    it('loads the snapshot again when a patch does not fit it', async () => {
        const {api, dashboard, socket} = wired()
        dashboard.start()
        await settle()
        socket().receive(patch(42, {path: 'nothing.here', op: 'REPLACE', value: 1}))
        await settle()
        expect(api.snapshot).toHaveBeenCalledTimes(2)
    })

    it('ignores a stream it replaced and reconnects once after a lost stream', async () => {
        const {dashboard, sockets} = wired()
        dashboard.start()
        await settle()
        const first = sockets[0] as FakeSocket
        first.emit('open')
        first.emit('close', {code: 1006, reason: ''})
        expect(dashboard.live.value).toBe(false)
        await vi.advanceTimersByTimeAsync(1000)
        expect(sockets).toHaveLength(2)
        first.receive(patch(42, {path: 'player.unreadInbox', op: 'REPLACE', value: 99}))
        first.emit('close', {code: 4401, reason: ''})
        await vi.advanceTimersByTimeAsync(60_000)
        expect(sockets).toHaveLength(2)
        expect(dashboard.status.value).toBe('ready')
        expect(dashboard.snapshot.value?.player.unreadInbox).toBe(3)
    })

    it('reconnects after the gateway went away or failed', async () => {
        const {dashboard, sockets, socket} = wired()
        dashboard.start()
        await settle()
        socket().emit('close', {code: 1001, reason: 'Idle'})
        await vi.advanceTimersByTimeAsync(1000)
        socket().emit('close', {code: 1011, reason: ''})
        await vi.advanceTimersByTimeAsync(999)
        expect(sockets).toHaveLength(2)
        await vi.advanceTimersByTimeAsync(1)
        expect(sockets).toHaveLength(3)
    })

    it('ends the session on 4401 and on session.closed', async () => {
        const {dashboard, socket} = wired()
        dashboard.start()
        await settle()
        socket().emit('close', {code: 4401, reason: 'Session ended'})
        expect(dashboard.status.value).toBe('gone')
        expect(vi.getTimerCount()).toBe(0)

        useDashboard().forget()
        const next = wired()
        next.dashboard.start()
        await settle()
        next.socket().receive({type: 'session.closed', v: 1, session: 's', player: ALEX, reason: 'LOGOUT'})
        expect(next.dashboard.status.value).toBe('gone')
    })

    it('shows the server as discontinued on 4403', async () => {
        const {api, dashboard, socket} = wired()
        dashboard.start()
        await settle()
        api.server.mockResolvedValueOnce(serverInfo({version: {status: 'DISCONTINUED', message: 'Update to 2.4.'}, online: false}))
        socket().emit('close', {code: 4403, reason: 'Discontinued'})
        await settle()
        expect(dashboard.status.value).toBe('discontinued')
        expect(dashboard.server.value?.version.message).toBe('Update to 2.4.')
        expect(vi.getTimerCount()).toBe(0)
    })

    it('stops for good on 1008 and keeps the reason', async () => {
        const {dashboard, sockets, socket} = wired()
        dashboard.start()
        await settle()
        socket().emit('close', {code: 1008, reason: 'Foreign origin'})
        await vi.advanceTimersByTimeAsync(60_000)
        expect(dashboard.status.value).toBe('refused')
        expect(dashboard.problem.value).toBe('Foreign origin')
        expect(sockets).toHaveLength(1)
    })

    it('goes on trying while the server is offline, and is gone on 401', async () => {
        const {api, dashboard} = wired()
        api.snapshot.mockRejectedValueOnce(gatewayError(503, {error: 'SERVER_OFFLINE'}))
        dashboard.start()
        await settle()
        expect(dashboard.status.value).toBe('offline')
        await vi.advanceTimersByTimeAsync(1000)
        expect(dashboard.status.value).toBe('ready')

        api.snapshot.mockRejectedValueOnce(gatewayError(401, {error: 'UNAUTHORIZED'}))
        dashboard.wake()
        await settle()
        expect(dashboard.status.value).toBe('gone')
    })

    it('feeds a delivery into the activity', async () => {
        const {dashboard, socket} = wired()
        dashboard.start()
        await settle()
        socket().receive(patch(42, {path: 'company.members', op: 'UPSERT', key: ALEX, value: member(ALEX, 'Alex', 2274)}))
        expect(dashboard.activity.value[0]).toMatchObject({kind: 'delivered', actor: 'Alex', amount: 64})
        expect(NODE).toBe(snapshot().node)
    })
})

/** The server shown while it is offline. */
describe('an offline server', () => {
    it('keeps the features of the last time it was online', () => {
        const before = serverInfo()
        const shown = serverToShow(before, serverInfo({online: false, features: []}))
        expect(shown.online).toBe(false)
        expect(shown.features).toEqual(before.features)
        expect(serverToShow(before, serverInfo({features: ['market']})).features).toEqual(['market'])
    })
})
