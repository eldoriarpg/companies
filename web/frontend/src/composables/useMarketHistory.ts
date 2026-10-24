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
 * The price history of one material over a period, loaded again when the material or the period changes; switching the
 * material drops the old history at once. The series has one slot per calendar day of the period the plugin answered
 * for, ending with the server's today, so the chart spaces days by time in the server's calendar; days without orders
 * or with too few for a price stay empty and leave a gap.
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

    watch(material, () => {
        detail.value = null
    })
    watch([material, range], () => void load(), {immediate: true})

    const series = computed(() => daily(detail.value))

    return {
        range,
        detail,
        status: readonly(status),
        series,
        priced: computed(() => series.value.filter(value => value !== null).length),
    }
}

/**
 * The average price of each calendar day of a history's period, null on days without one.
 *
 * @param detail the history, null while none is loaded
 * @returns one slot per day, oldest first, the last being the server's today
 */
export function daily(detail: MarketDetailView | null): (number | null)[] {
    if (detail === null) return []
    const prices = new Map(detail.history.map(point => [point.day, point.avg ?? null]))
    const end = Date.parse(`${detail.to}T00:00:00Z`)
    return Array.from({length: detail.days}, (_, index) => {
        const day = new Date(end - (detail.days - 1 - index) * 86_400_000).toISOString().slice(0, 10)
        return prices.get(day) ?? null
    })
}
