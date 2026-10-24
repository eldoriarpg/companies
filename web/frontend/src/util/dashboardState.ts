/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import type {ServerInfo} from '~/api/gateway'
import type {Snapshot} from '~/api/generated/protocol'
import type {DashboardStream} from '~/api/stream'
import type {ActivityEntry} from '~/util/snapshot/activity'

/** Where the dashboard stands with its session. */
export type DashboardStatus = 'idle' | 'loading' | 'ready' | 'offline' | 'gone' | 'discontinued'

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
    /** The company's feed, newest first. */
    activity: ActivityEntry[]
    nextActivity: number
    /** The treasury's balance as it changed while the dashboard was open, oldest first. */
    balances: BalancePoint[]
    /** How many reconnects failed in a row, for the backoff. */
    retries: number
}

/** Timers and the stream, which only the browser holds. */
export interface DashboardMachinery {
    stream: DashboardStream | null
    retry: ReturnType<typeof setTimeout> | null
}

/**
 * The state of the dashboard's session: the server, the snapshot as the patches changed it, the live feed. Held per
 * app, read and changed only through `useDashboard`.
 *
 * @returns the state and a reset that closes the stream
 */
export function dashboardState() {
    const data = useState<DashboardData>('dashboardState', () => ({
        status: 'idle', server: null, snapshot: null, seqs: {}, live: false, activity: [], nextActivity: 1, balances: [], retries: 0,
    }))
    const machinery = useState<DashboardMachinery>('dashboardState.machinery', () => ({stream: null, retry: null}))
    return {
        data,
        machinery,
        reset(): void {
            machinery.value.stream?.close()
            if (machinery.value.retry) clearTimeout(machinery.value.retry)
            machinery.value = {stream: null, retry: null}
            data.value = {
                status: 'idle', server: null, snapshot: null, seqs: {}, live: false, activity: [], nextActivity: 1, balances: [], retries: 0,
            }
        },
    }
}
