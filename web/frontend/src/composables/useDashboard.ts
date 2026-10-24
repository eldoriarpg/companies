/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {gatewayApi, gatewayFailure, type GatewayApi, type StreamMessage} from '~/api/gateway'
import type {Patch, Snapshot} from '~/api/generated/protocol'
import {openStream} from '~/api/stream'
import {dashboardState} from '~/util/dashboardState'
import {activityOf, addActivity} from '~/util/snapshot/activity'
import {applyPatch, arrivalOf} from '~/util/snapshot/patch'

export type {DashboardStatus} from '~/util/dashboardState'

/** The close code with which the gateway ends a stream whose session is gone. */
export const SESSION_GONE = 4401

/** The close code with which the gateway ends a stream of a discontinued server. */
export const DISCONTINUED = 4403

const FIRST_RETRY_MILLIS = 1000
const LAST_RETRY_MILLIS = 30_000
const BALANCE_POINTS = 200

/**
 * The dashboard's session: the server and its features, the snapshot kept in step with the patches the stream
 * brings, and the company's live feed read from them.
 *
 * <p>The snapshot is replaced as a whole on every patch and never changed in place, so it is kept out of Vue's deep
 * reactivity (`markRaw`). A patch the snapshot already holds is dropped; a gap in a node's numbers, a `resync` from the
 * gateway and every reconnect load the snapshot again, since patches may have been missed meanwhile. A lost stream
 * reconnects with a backoff from one second doubling to thirty; waking the page reconnects at once.
 *
 * @param api the gateway's calls, for tests
 * @returns the session's state and what can be done with it
 */
export function useDashboard(api: GatewayApi = gatewayApi()) {
    const {data, machinery, reset} = dashboardState()

    /** Loads the server and the snapshot, then opens the stream. */
    async function load(): Promise<void> {
        try {
            const server = await api.server()
            data.value.server = server
            if (server.version.status === 'DISCONTINUED') {
                data.value.status = 'discontinued'
                return
            }
            take(await api.snapshot())
            data.value.status = 'ready'
            data.value.retries = 0
            connect()
        } catch (error) {
            fail(error)
        }
    }

    /**
     * Shows a snapshot and starts counting the patches of its node from it.
     *
     * @param snapshot the snapshot
     */
    function take(snapshot: Snapshot): void {
        data.value.snapshot = markRaw(snapshot)
        data.value.seqs = {[snapshot.node]: snapshot.seq}
        noteBalance(snapshot)
    }

    /**
     * Tells why loading failed: the session is gone, the server is discontinued, or the gateway or the server cannot
     * be reached right now, which is tried again.
     *
     * @param error what the call threw
     */
    function fail(error: unknown): void {
        const {status, body} = gatewayFailure(error)
        if (status === 401 || status === 404 || body?.error === 'UNAUTHORIZED' || body?.error === 'UNKNOWN_SESSION') {
            data.value.status = 'gone'
        } else if (body?.error === 'DISCONTINUED') {
            if (data.value.server && body.version) data.value.server = {...data.value.server, version: body.version}
            data.value.status = 'discontinued'
        } else {
            data.value.status = data.value.snapshot ? 'ready' : 'offline'
            retryLater()
        }
    }

    /** Opens the stream, replacing one that still stands. */
    function connect(): void {
        machinery.value.stream?.close()
        machinery.value.stream = markRaw(openStream({
            opened: () => {
                data.value.live = true
            },
            message: receive,
            closed: code => {
                data.value.live = false
                if (code === SESSION_GONE) data.value.status = 'gone'
                else if (code === DISCONTINUED) void load()
                else if (data.value.status === 'ready') retryLater()
            },
        }))
    }

    /** Loads everything again after the backoff. */
    function retryLater(): void {
        if (machinery.value.retry) clearTimeout(machinery.value.retry)
        const wait = Math.min(LAST_RETRY_MILLIS, FIRST_RETRY_MILLIS * 2 ** data.value.retries)
        data.value.retries++
        machinery.value.retry = setTimeout(() => {
            machinery.value.retry = null
            void load()
        }, wait)
    }

    /**
     * Handles a message of the stream.
     *
     * @param message the message
     */
    function receive(message: StreamMessage): void {
        if (message.type === 'patch') receivePatch(message)
        else if (message.type === 'resync') void load()
        else if (message.type === 'server') data.value.server = message.server
        else if (message.type === 'session.closed') data.value.status = 'gone'
        else if (message.type === 'error' && message.code === 'UNAUTHORIZED') data.value.status = 'gone'
    }

    /**
     * Applies a patch, adds what it shows to the feed, or loads the snapshot again on a gap.
     *
     * @param patch the patch
     */
    function receivePatch(patch: Patch): void {
        const before = data.value.snapshot
        if (!before) return
        const arrival = arrivalOf(data.value.seqs, patch)
        if (arrival === 'old') return
        if (arrival === 'gap') {
            void load()
            return
        }
        const after = applyPatch(before, patch)
        data.value.seqs = {...data.value.seqs, [patch.node]: patch.seq}
        data.value.snapshot = markRaw(after)
        for (const entry of activityOf(before.company, after.company, patch, new Date().toISOString())) {
            data.value.activity = addActivity(data.value.activity, {...entry, id: String(data.value.nextActivity++)})
        }
        noteBalance(after)
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
        activity: computed(() => data.value.activity),
        balances: computed(() => data.value.balances),

        /**
         * Whether the server supports a feature.
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
            void load()
        },

        /** Reconnects at once when the page wakes up without a stream, after a phone slept or changed network. */
        wake(): void {
            if (data.value.live || data.value.status === 'idle' || data.value.status === 'gone') return
            if (machinery.value.retry) clearTimeout(machinery.value.retry)
            machinery.value.retry = null
            void load()
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

        /** Forgets the session here, after the gateway ended it. */
        forget(): void {
            reset()
        },
    }
}
