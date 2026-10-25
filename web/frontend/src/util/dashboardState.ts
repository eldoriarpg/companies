/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {ServerInfo} from '~/api/gateway'
import type {Patch, Snapshot} from '~/api/generated/protocol'
import type {DashboardStream} from '~/api/stream'
import type {ActivityEntry} from '~/util/snapshot/activity'

/**
 * Where the dashboard stands with its session: `refused` when the gateway refused the stream for good, such as for a
 * foreign origin, `offline` while neither the gateway nor the server can be reached and nothing was loaded yet.
 */
export type DashboardStatus = 'idle' | 'loading' | 'ready' | 'offline' | 'gone' | 'discontinued' | 'refused'

/** One value of the treasury as the dashboard saw it. */
export interface BalancePoint {
    time: string
    balance: number
}

/** Everything the dashboard knows about its session. */
export interface DashboardData {
    status: DashboardStatus
    server: ServerInfo | null
    snapshot: Snapshot | null
    /** The last patch number seen per node. */
    seqs: Record<string, number>
    /** Whether the stream stands. */
    live: boolean
    /** Whether the player is online on the server, from the snapshot and the gateway's presence events. */
    playerOnline: boolean
    /** The company's feed, newest first. */
    activity: ActivityEntry[]
    nextActivity: number
    /** The treasury's balance as it changed while the dashboard was open, oldest first. */
    balances: BalancePoint[]
    /** How many reconnects failed in a row, for the backoff. */
    retries: number
    /** The gateway's reason when it refused the stream for good. */
    problem: string | null
}

/** Timers and the stream, which only the browser holds. */
export interface DashboardMachinery {
    stream: DashboardStream | null
    /** Counts the streams opened, so the events of one replaced since are told apart and ignored. */
    generation: number
    retry: ReturnType<typeof setTimeout> | null
    /** The patches that arrive while the snapshot loads; none while no snapshot is loading. */
    buffer: Patch[] | null
    /** Whether a snapshot is loading. */
    loading: boolean
    /** Whether the snapshot is to be loaded once more after the load under way, because a resync came meanwhile. */
    reloadAgain: boolean
    /** Whether a snapshot loaded since the stream was last opened; with a live stream it ends the backoff. */
    loadedSinceOpen: boolean
}

/**
 * The state of the dashboard's session: the server, the snapshot as the patches changed it, the live feed. Held per
 * app, read and changed only through `useDashboard`.
 *
 * @returns the state and a reset that closes the stream
 */
export function dashboardState() {
    const data = useState<DashboardData>('dashboardState', () => ({
        status: 'idle', server: null, snapshot: null, seqs: {}, live: false, playerOnline: false, activity: [], nextActivity: 1, balances: [], retries: 0,
        problem: null,
    }))
    const machinery = useState<DashboardMachinery>('dashboardState.machinery', () => ({
        stream: null, generation: 0, retry: null, buffer: null, loading: false, reloadAgain: false,
        loadedSinceOpen: false,
    }))
    return {
        data,
        machinery,
        reset(): void {
            machinery.value.stream?.close()
            if (machinery.value.retry) clearTimeout(machinery.value.retry)
            machinery.value = {
                stream: null, generation: machinery.value.generation + 1, retry: null, buffer: null, loading: false, reloadAgain: false,
                loadedSinceOpen: false,
            }
            data.value = {
                status: 'idle', server: null, snapshot: null, seqs: {}, live: false, playerOnline: false, activity: [], nextActivity: 1, balances: [], retries: 0,
                problem: null,
            }
        },
    }
}
