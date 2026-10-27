/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {watchDebounced} from '@vueuse/core'
import {gatewayApi, type GatewayApi, type OrderListing, type OrderSearchArgs, type OrderSearchPage} from '~/api/gateway'

/** The quick filters of the order book. */
export type OrderFilter = 'all' | 'small' | 'large-payout' | 'server' | 'players'

/** The sortings of the in-game search the order book offers. */
export const ORDER_SORTINGS = ['AGE', 'PRICE', 'UNIT_PRICE', 'SIZE', 'NAME'] as const

/** One sorting of the order book. */
export type OrderSorting = (typeof ORDER_SORTINGS)[number]

const PAGE_SIZE = 25
const SEARCH_DELAY_MILLIS = 300
const SMALL_ORDER = 256
const LARGE_PAYOUT = 3000

/**
 * The search arguments for the order book's state.
 *
 * @param name the text searched for
 * @param filter the quick filter
 * @param sort the sorting
 * @param page the page
 * @returns the arguments
 */
export function searchArgs(name: string, filter: OrderFilter, sort: OrderSorting, page: number): OrderSearchArgs {
    return {
        name: name.trim() === '' ? undefined : name.trim(),
        maxOrderSize: filter === 'small' ? SMALL_ORDER : undefined,
        minPrice: filter === 'large-payout' ? LARGE_PAYOUT : undefined,
        serverOrders: filter === 'server' ? 'only' : filter === 'players' ? 'hide' : undefined,
        sort,
        asc: sort === 'NAME',
        page,
        pageSize: PAGE_SIZE,
    }
}

/**
 * The order book: the open orders the plugin finds for a search, a page at a time, and the details of one. Results
 * are not live; a new search shows what changed.
 *
 * @param api the gateway's calls, for tests
 * @returns the search, its results and the details
 */
export function useOrderBook(api: GatewayApi = gatewayApi()) {
    const query = ref('')
    const filter = ref<OrderFilter>('all')
    const sort = ref<OrderSorting>('AGE')
    const pageNumber = ref(1)
    const result = shallowRef<OrderSearchPage | null>(null)
    const status = ref<'loading' | 'ready' | 'failed'>('loading')
    const detail = shallowRef<OrderListing | null>(null)
    const latest = useLatest()
    const details = useLatest()

    /** Loads the current page of the search; an answer overtaken by a newer search is dropped. */
    async function load(): Promise<void> {
        const ticket = latest.next()
        status.value = 'loading'
        try {
            const page = await api.read('orders.browse.search', searchArgs(query.value, filter.value, sort.value, pageNumber.value))
            if (!latest.is(ticket)) return
            result.value = page
            status.value = 'ready'
        } catch {
            if (latest.is(ticket)) status.value = 'failed'
        }
    }

    /**
     * Finds an order for its details: from the page shown, or from the plugin.
     *
     * @param id the order's id, none to close the details
     */
    async function open(id: number | null): Promise<void> {
        const ticket = details.next()
        if (id === null) {
            detail.value = null
            return
        }
        detail.value = result.value?.orders.find(order => order.id === id) ?? null
        if (detail.value) return
        try {
            const order = await api.read('orders.browse.details', {order: id})
            if (details.is(ticket)) detail.value = order
        } catch {
            if (details.is(ticket)) detail.value = null
        }
    }

    watchDebounced(query, () => {
        pageNumber.value = 1
        void load()
    }, {debounce: SEARCH_DELAY_MILLIS})
    watch([filter, sort], () => {
        pageNumber.value = 1
        void load()
    })
    watch(pageNumber, () => void load())

    return {query, filter, sort, page: pageNumber, result, status: readonly(status), detail, load, open}
}
