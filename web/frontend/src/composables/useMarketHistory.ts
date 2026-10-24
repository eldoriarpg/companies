/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {gatewayApi, type GatewayApi, type MarketDetailView} from '~/api/gateway'

/** The periods a material's price history can be shown for, in days. */
export const HISTORY_RANGES = [7, 30, 90] as const

/** A period of the price history, in days. */
export type HistoryRange = typeof HISTORY_RANGES[number]

/**
 * The price history of one material over a period, loaded again when the material or the period changes. Days with
 * too few orders carry no price and leave a gap the chart skips.
 *
 * @param material the material, null while none is open
 * @param api the gateway's calls, for tests
 * @returns the period, the history, its state and the prices to draw
 */
export function useMarketHistory(material: Ref<string | null>, api: GatewayApi = gatewayApi()) {
    const range = ref<HistoryRange>(30)
    const detail = shallowRef<MarketDetailView | null>(null)
    const status = ref<'idle' | 'loading' | 'ready' | 'failed'>('idle')
    const latest = useLatest()

    /** Loads the history; an answer overtaken by a newer request is dropped. */
    async function load(): Promise<void> {
        const ticket = latest.next()
        if (material.value === null) {
            detail.value = null
            status.value = 'idle'
            return
        }
        status.value = 'loading'
        try {
            const answer = await api.read('market.item', {material: material.value, days: range.value})
            if (!latest.is(ticket)) return
            detail.value = answer
            status.value = 'ready'
        } catch {
            if (latest.is(ticket)) status.value = 'failed'
        }
    }

    watch([material, range], () => void load(), {immediate: true})

    return {
        range,
        detail,
        status: readonly(status),
        prices: computed(() => (detail.value?.history ?? []).flatMap(point => (point.avg === undefined ? [] : [point.avg]))),
    }
}
