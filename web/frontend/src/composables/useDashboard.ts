/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {gatewayApi, gatewayFailure, type GatewayApi, type ServerInfo, type StreamMessage} from '~/api/gateway'
import type {Patch, Snapshot} from '~/api/generated/protocol'
import {openStream, type SocketFactory} from '~/api/stream'
import {dashboardState} from '~/util/dashboardState'
import {activityOf, addActivity} from '~/util/snapshot/activity'
import {applyPatch, isNewer, knownRoot, Unapplicable} from '~/util/snapshot/patch'

export type {DashboardStatus} from '~/util/dashboardState'

/** The close code with which the gateway ends a stream whose session is gone. */
export const SESSION_GONE = 4401

/** The close code with which the gateway ends a stream of a discontinued server. */
export const DISCONTINUED = 4403

/** The close code of a stream the gateway refuses for good, such as one from a foreign origin. */
export const POLICY_VIOLATION = 1008

const FIRST_RETRY_MILLIS = 1000
const LAST_RETRY_MILLIS = 30_000
const BALANCE_POINTS = 200

/** What the dashboard talks to, replaced in tests. */
export interface DashboardWiring {
    api?: GatewayApi
    connect?: SocketFactory
}

/**
 * The server as the dashboard keeps it: while no node of the server is online, the features of the last time one
 * was are kept, so the navigation does not shrink while the server restarts.
 *
 * @param before the server known so far
 * @param next the server as the gateway sends it now
 * @returns the server to show
 */
export function serverToShow(before: ServerInfo | null, next: ServerInfo): ServerInfo {
    if (next.online || !before) return next
    return {...next, features: before.features}
}

/**
 * The dashboard's session: the server and its features, the snapshot kept in step with the patches the stream
 * brings, and the company's live feed read from them.
 *
 * <p>The stream opens first and the snapshot loads after it; patches arriving meanwhile wait and are applied above the
 * snapshot's sequence once it is there, so none is lost. Each node numbers its patches across all its sessions, so
 * gaps are normal here: a patch at or below the last of its node is dropped and every other one applied. Finding real
 * gaps is the gateway's job; its `resync` loads the snapshot again without touching the stream. A patch that does
 * not fit the snapshot loads it again too, unless it changes a part this version does not know, which is dropped as
 * the gateway drops it.
 *
 * <p>A stream that is replaced is detached first, so its end is never taken for a lost stream. A lost stream is
 * opened again with a backoff from one second doubling to thirty, and the snapshot reloaded, since patches may have
 * been missed; the backoff starts over only once a stream opens, and waking the page tries at once. The gateway's close codes end the session (4401), show the server as
 * discontinued (4403), or refuse the dashboard for good (1008).
 *
 * <p>The snapshot is replaced as a whole on every patch and never changed in place, so it is kept out of Vue's deep
 * reactivity (`markRaw`).
 *
 * @param wiring the gateway's calls and the socket factory, for tests
 * @returns the session's state and what can be done with it
 */
export function useDashboard(wiring: DashboardWiring = {}) {
    const api = wiring.api ?? gatewayApi()
    const {data, machinery, reset} = dashboardState()

    /** Opens the stream, replacing and detaching one that still stands. */
    function connect(): void {
        machinery.value.stream?.close()
        const generation = ++machinery.value.generation
        const current = () => machinery.value.generation === generation
        machinery.value.stream = markRaw(openStream({
            opened: () => {
                if (!current()) return
                data.value.live = true
                data.value.retries = 0
            },
            message: message => {
                if (current()) receive(message)
            },
            closed: (code, reason) => {
                if (current()) closed(code, reason)
            },
        }, wiring.connect))
    }

    /**
     * Whether the session cannot go on, so an answer arriving now is dropped.
     *
     * @returns true once it is gone, discontinued, refused or forgotten
     */
    function ended(): boolean {
        return ['idle', 'gone', 'discontinued', 'refused'].includes(data.value.status)
    }

    /** Stops the stream and every timer, for a session that cannot go on. */
    function stop(): void {
        machinery.value.generation++
        machinery.value.stream?.close()
        machinery.value.stream = null
        if (machinery.value.retry) clearTimeout(machinery.value.retry)
        machinery.value.retry = null
        machinery.value.buffer = null
        machinery.value.reloadAgain = false
        data.value.live = false
    }

    /** Opens the stream and loads the server and the snapshot. */
    async function open(): Promise<void> {
        connect()
        machinery.value.buffer = machinery.value.buffer ?? []
        try {
            showServer(await api.server())
        } catch (error) {
            if (!ended()) fail(error)
            return
        }
        if (ended()) return
        if (data.value.server?.version.status === 'DISCONTINUED') {
            stop()
            data.value.status = 'discontinued'
            return
        }
        await loadSnapshot()
    }

    /** Loads the snapshot, holding back the patches that arrive meanwhile, and applies them above its sequence. */
    async function loadSnapshot(): Promise<void> {
        if (machinery.value.loading) {
            machinery.value.reloadAgain = true
            return
        }
        machinery.value.loading = true
        machinery.value.buffer = machinery.value.buffer ?? []
        let snapshot: Snapshot
        try {
            snapshot = await api.snapshot()
        } catch (error) {
            machinery.value.loading = false
            if (!ended()) fail(error)
            return
        }
        machinery.value.loading = false
        if (ended()) return
        take(snapshot)
        if (machinery.value.reloadAgain) {
            machinery.value.reloadAgain = false
            await loadSnapshot()
        }
    }

    /**
     * Shows a snapshot, starts counting the patches of its node from it, and applies those that waited.
     *
     * @param snapshot the snapshot
     */
    function take(snapshot: Snapshot): void {
        const waiting = machinery.value.buffer ?? []
        machinery.value.buffer = null
        forgetOtherCompany(data.value.snapshot, snapshot)
        data.value.snapshot = markRaw(snapshot)
        data.value.playerOnline = snapshot.player.online === true
        data.value.seqs = {[snapshot.node]: snapshot.seq}
        data.value.status = 'ready'
        noteBalance(snapshot)
        for (const patch of waiting) receivePatch(patch)
    }

    /**
     * Shows the server as the gateway sends it.
     *
     * @param next the server
     */
    function showServer(next: ServerInfo): void {
        data.value.server = serverToShow(data.value.server, next)
    }

    /**
     * Shows whether the session's player is online on the server, as the gateway reports it. It is kept beside the
     * snapshot, since a patch replacing the player carries no such flag.
     *
     * @param online whether the player is online
     */
    function showPresence(online: boolean): void {
        data.value.playerOnline = online
    }

    /**
     * Tells why loading failed: the session is gone, the server is discontinued, or the gateway or the server cannot
     * be reached right now, which is tried again.
     *
     * @param error what the call threw
     */
    function fail(error: unknown): void {
        const {status, body} = gatewayFailure(error)
        if (body?.error === 'DISCONTINUED') {
            stop()
            if (data.value.server && body.version) data.value.server = {...data.value.server, version: body.version}
            data.value.problem = body.message ?? null
            data.value.status = 'discontinued'
        } else if (status === 401 || body?.error === 'UNAUTHORIZED' || body?.error === 'UNKNOWN_SESSION') {
            stop()
            data.value.status = 'gone'
        } else {
            data.value.status = data.value.snapshot ? 'ready' : 'offline'
            retryLater()
        }
    }

    /**
     * Handles the end of the current stream by its close code.
     *
     * @param code the close code
     * @param reason the reason the gateway gave
     */
    function closed(code: number, reason: string): void {
        data.value.live = false
        if (code === SESSION_GONE) {
            stop()
            data.value.status = 'gone'
        } else if (code === DISCONTINUED) {
            stop()
            void api.server().then(showServer, () => undefined).finally(() => {
                data.value.status = 'discontinued'
            })
        } else if (code === POLICY_VIOLATION) {
            stop()
            data.value.problem = reason || null
            data.value.status = 'refused'
        } else {
            retryLater()
        }
    }

    /** Opens everything again after the backoff. */
    function retryLater(): void {
        if (machinery.value.retry) clearTimeout(machinery.value.retry)
        const wait = Math.min(LAST_RETRY_MILLIS, FIRST_RETRY_MILLIS * 2 ** data.value.retries)
        data.value.retries++
        machinery.value.retry = setTimeout(() => {
            machinery.value.retry = null
            void open()
        }, wait)
    }

    /**
     * Handles a message of the stream.
     *
     * @param message the message
     */
    function receive(message: StreamMessage): void {
        if (message.type === 'patch') receivePatch(message)
        else if (message.type === 'resync') void loadSnapshot()
        else if (message.type === 'server') showServer(message.server)
        else if (message.type === 'presence') showPresence(message.online)
        else if (message.type === 'session.closed' || (message.type === 'error' && message.code === 'UNAUTHORIZED')) {
            stop()
            data.value.status = 'gone'
        }
    }

    /**
     * Applies a patch and adds what it shows to the feed; holds it back while the snapshot loads, and loads the
     * snapshot again when the patch does not fit it.
     *
     * @param patch the patch
     */
    function receivePatch(patch: Patch): void {
        if (machinery.value.buffer !== null) {
            machinery.value.buffer.push(patch)
            return
        }
        const before = data.value.snapshot
        if (!before || !isNewer(data.value.seqs, patch)) return
        let after: Snapshot
        try {
            after = applyPatch(before, patch)
        } catch (error) {
            if (error instanceof Unapplicable && knownRoot(patch)) void loadSnapshot()
            return
        }
        data.value.seqs = {...data.value.seqs, [patch.node]: patch.seq}
        forgetOtherCompany(before, after)
        data.value.snapshot = markRaw(after)
        for (const entry of activityOf(before.company, after.company, patch, new Date().toISOString())) {
            data.value.activity = addActivity(data.value.activity, {...entry, id: String(data.value.nextActivity++)})
        }
        noteBalance(after)
    }

    /**
     * Forgets the balances noted for a company the player is no longer in, so the treasury's line and its change
     * belong to one company only.
     *
     * @param before the snapshot shown so far, none before the first
     * @param after the snapshot shown next
     */
    function forgetOtherCompany(before: Snapshot | null, after: Snapshot): void {
        if (before && before.company?.id !== after.company?.id) data.value.balances = []
    }

    /**
     * Notes the treasury's balance when it changed.
     *
     * @param snapshot the snapshot
     */
    function noteBalance(snapshot: Snapshot): void {
        const balance = snapshot.company?.treasury?.balance
        if (balance === undefined || data.value.balances.at(-1)?.balance === balance) return
        data.value.balances = [...data.value.balances, {time: new Date().toISOString(), balance}].slice(-BALANCE_POINTS)
    }

    return {
        status: computed(() => data.value.status),
        server: computed(() => data.value.server),
        snapshot: computed(() => data.value.snapshot),
        company: computed(() => data.value.snapshot?.company ?? null),
        live: computed(() => data.value.live),
        online: computed(() => data.value.server?.online ?? true),
        playerOnline: computed(() => data.value.playerOnline),
        problem: computed(() => data.value.problem),
        activity: computed(() => data.value.activity),
        balances: computed(() => data.value.balances),

        /**
         * Whether the server supports a feature, as far as the dashboard last knew.
         *
         * @param feature the feature's name
         * @returns true when its plugin lists it
         */
        supports(feature: string): boolean {
            return data.value.server?.features.includes(feature) ?? false
        },

        /** Opens the session once; later calls leave it as it is. */
        start(): void {
            if (data.value.status !== 'idle') return
            data.value.status = 'loading'
            void open()
        },

        /** Opens everything again at once when the page wakes without a stream, after a phone slept or changed network. */
        wake(): void {
            if (data.value.live || !['ready', 'offline', 'loading'].includes(data.value.status)) return
            if (machinery.value.retry) clearTimeout(machinery.value.retry)
            machinery.value.retry = null
            void open()
        },

        /** Ends the session at the gateway and forgets it here. */
        async logout(): Promise<void> {
            try {
                await api.logout()
            } finally {
                reset()
                data.value.status = 'gone'
            }
        },

        /** Forgets the session here, after the gateway ended it or before another login. */
        forget(): void {
            reset()
        },
    }
}
