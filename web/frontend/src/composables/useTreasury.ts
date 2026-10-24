/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {watchDebounced} from '@vueuse/core'
import {gatewayApi, type GatewayApi, type TreasuryLogEntry} from '~/api/gateway'
import {Feature} from '~/api/generated/protocol'
import {useDashboard} from './useDashboard'

/** How many of the latest movements the treasury shows. */
const RECENT = 5

/** How long the balance has to rest before the log is loaded again, so a burst of payouts loads it once. */
const RELOAD_DELAY_MILLIS = 1500

/**
 * The company's treasury over time: the balances of the treasury log's first page, where the server has the treasury
 * feature and the player may see it, followed by the balances the patches brought while the page was open. Once the
 * log was loaded, a changed balance loads it again shortly after, so the latest movements stay live; a switch to
 * another company drops the old company's log and loads the new one's.
 *
 * @param api the gateway's calls, for tests
 * @returns the balance now, the series, the change over it and the latest movements
 */
export function useTreasury(api: GatewayApi = gatewayApi()) {
    const {company, balances, supports} = useDashboard({api})
    const log = shallowRef<TreasuryLogEntry[]>([])
    const latest = useLatest()
    const loaded = ref(false)

    /** Loads the log once, where it is available. */
    async function loadLog(): Promise<void> {
        if (!supports(Feature['company.treasury']) || !company.value?.treasury) return
        const ticket = latest.next()
        try {
            const entries = (await api.read('company.treasury.log', {page: 1})).entries
            if (!latest.is(ticket)) return
            log.value = entries
            loaded.value = true
        } catch {
            if (latest.is(ticket) && !loaded.value) log.value = []
        }
    }

    watch(() => company.value?.id, (id, before) => {
        if (id === before || !loaded.value) return
        latest.next()
        log.value = []
        loaded.value = false
        void loadLog()
    })

    watchDebounced(() => company.value?.treasury?.balance, () => {
        if (loaded.value) void loadLog()
    }, {debounce: RELOAD_DELAY_MILLIS})

    const series = computed(() => {
        const history = [...log.value].sort((a, b) => a.time.localeCompare(b.time)).map(entry => entry.balance)
        const since = log.value.reduce((latest, entry) => (entry.time > latest ? entry.time : latest), '')
        const live = balances.value.filter(point => point.time > since).map(point => point.balance)
        return [...history, ...live]
    })

    return {
        balance: computed(() => company.value?.treasury?.balance ?? null),
        series,
        change: computed(() => {
            const first = series.value[0]
            const last = series.value.at(-1)
            return first === undefined || last === undefined ? 0 : last - first
        }),
        start: computed(() => series.value[0] ?? 0),
        recent: computed(() => log.value.slice(0, RECENT)),
        loadLog,
    }
}
