/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {gatewayApi, type GatewayApi} from '~/api/gateway'
import type {RankingPage} from '~/api/generated/protocol'
import {useDashboard} from './useDashboard'

/** The rankings the plugin keeps, as their keys travel. */
export const RANKINGS = ['orders', 'earned', 'items_delivered', 'member', 'level'] as const

/** The time ranges of a ranking, as their keys travel. */
export const RANGES = ['all', '30d', '7d'] as const

/**
 * The rankings of the companies: the first page of the default ranking comes with the snapshot, every other ranking,
 * range and page from the plugin.
 *
 * @param api the gateway's calls, for tests
 * @returns the choice of ranking, range and page, and the page shown
 */
export function useRankings(api: GatewayApi = gatewayApi()) {
    const {snapshot} = useDashboard({api})
    const ranking = ref(snapshot.value?.rankings.ranking ?? 'orders')
    const range = ref(snapshot.value?.rankings.range ?? 'all')
    const page = ref(1)
    const loaded = shallowRef<RankingPage | null>(null)
    const status = ref<'ready' | 'loading' | 'failed'>('ready')
    const latest = useLatest()

    /** Loads the chosen page; an answer overtaken by a newer choice is dropped. */
    async function load(): Promise<void> {
        const ticket = latest.next()
        status.value = 'loading'
        try {
            const answer = await api.read('rankings.page', {ranking: ranking.value, range: range.value, page: page.value})
            if (!latest.is(ticket)) return
            loaded.value = answer
            status.value = 'ready'
        } catch {
            if (latest.is(ticket)) status.value = 'failed'
        }
    }

    watch([ranking, range], () => {
        page.value = 1
        void load()
    })
    watch(page, () => void load())

    return {
        ranking,
        range,
        page,
        status: readonly(status),
        shown: computed(() => loaded.value ?? snapshot.value?.rankings ?? null),
    }
}
