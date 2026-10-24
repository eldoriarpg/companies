/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {gatewayApi, type GatewayApi, type TreasuryLogEntry} from '~/api/gateway'
import {Feature} from '~/api/generated/protocol'
import {useDashboard} from './useDashboard'

/**
 * The company's treasury over time: the balances of the treasury log's first page, where the server has the treasury
 * feature and the player may see it, followed by the balances the patches brought while the page was open.
 *
 * @param api the gateway's calls, for tests
 * @returns the balance now, the series and the change over it
 */
export function useTreasury(api: GatewayApi = gatewayApi()) {
    const {company, balances, supports} = useDashboard(api)
    const log = shallowRef<TreasuryLogEntry[]>([])

    /** Loads the log once, where it is available. */
    async function loadLog(): Promise<void> {
        if (!supports(Feature['company.treasury']) || !company.value?.treasury) return
        try {
            log.value = (await api.read('company.treasury.log', {page: 1})).entries
        } catch {
            log.value = []
        }
    }

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
        loadLog,
    }
}
