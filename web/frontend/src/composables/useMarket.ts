/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {watchDebounced} from '@vueuse/core'
import {gatewayApi, type GatewayApi, type MarketItemView} from '~/api/gateway'
import {Feature} from '~/api/generated/protocol'
import {marketState} from '~/util/marketState'
import {useDashboard} from './useDashboard'

/** How long the typing pauses before a search goes out. */
const SEARCH_DELAY_MILLIS = 300

/**
 * The market: price statistics per material from the plugin. The most ordered items are loaded once and shared;
 * the biggest movers, the items whose price changed most against the week before, are loaded when first shown; a
 * search asks the plugin for the materials matching its text and wins over both. Prices stay out where too few orders
 * back them.
 *
 * @param api the gateway's calls, for tests
 * @returns the market's items, the averages by material and the search
 */
export function useMarket(api: GatewayApi = gatewayApi()) {
    const state = marketState()
    const {supports} = useDashboard({api})
    const query = ref('')
    const found = ref<MarketItemView[] | null>(null)
    const searching = ref(false)
    const latest = useLatest()
    const view = ref<'top' | 'movers'>('top')
    const movers = shallowRef<MarketItemView[] | null>(null)
    const moversStatus = ref<'idle' | 'loading' | 'ready' | 'failed'>('idle')

    /** Loads the most ordered items once, where the server has the market. */
    async function loadTop(): Promise<void> {
        if (state.value.status !== 'idle' || !supports(Feature.market)) return
        state.value.status = 'loading'
        try {
            state.value.top = (await api.read('market.top', {page: 1})).items
            state.value.status = 'ready'
        } catch {
            state.value.status = 'failed'
        }
    }

    /** Asks the plugin for the materials matching the search; an answer overtaken by a newer search is dropped. */
    async function search(): Promise<void> {
        const ticket = latest.next()
        const text = query.value.trim()
        if (text === '') {
            found.value = null
            searching.value = false
            return
        }
        searching.value = true
        let items: MarketItemView[]
        try {
            items = (await api.read('market.search', {query: text, page: 1})).items
        } catch {
            items = []
        }
        if (!latest.is(ticket)) return
        found.value = items
        searching.value = false
    }

    /** Loads the biggest movers once; a failed load is tried again the next time they are shown. */
    async function loadMovers(): Promise<void> {
        if (moversStatus.value === 'loading' || moversStatus.value === 'ready') return
        moversStatus.value = 'loading'
        try {
            movers.value = (await api.read('market.movers', {page: 1})).items
            moversStatus.value = 'ready'
        } catch {
            moversStatus.value = 'failed'
        }
    }

    watchDebounced(query, () => void search(), {debounce: SEARCH_DELAY_MILLIS})
    watch(view, value => {
        if (value === 'movers') void loadMovers()
    })

    return {
        query,
        status: computed(() => state.value.status),
        moversStatus: readonly(moversStatus),
        searching: readonly(searching),
        top: computed(() => state.value.top),
        view,
        items: computed(() => found.value ?? (view.value === 'movers' ? movers.value ?? [] : state.value.top)),
        averages: computed<ReadonlyMap<string, number>>(() => new Map(state.value.top
            .filter(entry => entry.avg !== undefined)
            .map(entry => [entry.item.material, entry.avg as number]))),
        loadTop,
        loadMovers,
    }
}
