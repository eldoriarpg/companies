/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {watchDebounced} from '@vueuse/core'
import {ActionRefused, gatewayApi, type GatewayApi, type KnownItems, type OrderCatalogue, type OrderQuote} from '~/api/gateway'
import {type DraftLine, type DraftRepetition, type LineSource, orderArgs} from '~/util/orderDraft'
import {useAction} from './useAction'

const QUOTE_DELAY_MILLIS = 400

/** Where the quote of the order being built stands. */
export type QuoteState =
    | {status: 'incomplete'}
    | {status: 'quoting'}
    | {status: 'quoted', quote: OrderQuote}
    | {status: 'refused', text: string}
    | {status: 'failed'}

/**
 * An order being built on the dashboard: its name, lines and repetition, the catalogue and known items they are
 * picked from, and the plugin's quote, asked for again whenever the order changes and is complete. An answer
 * overtaken by a newer change is dropped. Placing it charges the player and creates the order.
 *
 * @param api the gateway, for tests
 * @returns the order being built and what it is built with
 */
export function useOrderBuilder(api: GatewayApi = gatewayApi()) {
    const catalogue = shallowRef<OrderCatalogue | null>(null)
    const known = shallowRef<KnownItems | null>(null)
    const status = ref<'idle' | 'loading' | 'ready' | 'failed'>('idle')
    const name = ref('')
    const lines = ref<DraftLine[]>([])
    const repetition = ref<DraftRepetition>({enabled: false, interval: 'weekly', end: 'times', times: undefined, until: ''})
    const quote = shallowRef<QuoteState>({status: 'incomplete'})
    const place = useAction(api)
    const latest = useLatest()
    let nextId = 1

    const args = computed(() => orderArgs(name.value, lines.value, repetition.value))

    /** Loads the catalogue and the player's known items. */
    async function load(): Promise<void> {
        if (status.value === 'loading' || status.value === 'ready') return
        status.value = 'loading'
        try {
            const [loadedCatalogue, loadedKnown] = await Promise.all([
                api.read('orders.create.catalogue', {}),
                api.read('orders.create.items', {}),
            ])
            catalogue.value = loadedCatalogue
            known.value = loadedKnown
            status.value = 'ready'
        } catch {
            status.value = 'failed'
        }
    }

    /**
     * Adds a line.
     *
     * @param source the line's item
     * @param unitPrice the suggested price of one item
     */
    function addLine(source: LineSource, unitPrice?: number): void {
        lines.value = [...lines.value, {id: nextId++, source, amount: 1, unitPrice}]
    }

    /**
     * Removes a line.
     *
     * @param id the line
     */
    function removeLine(id: number): void {
        lines.value = lines.value.filter(line => line.id !== id)
    }

    /** Asks the plugin what the order costs, or why it would refuse it. */
    async function requote(): Promise<void> {
        const ticket = latest.next()
        const order = args.value
        if (order === null) {
            quote.value = {status: 'incomplete'}
            return
        }
        quote.value = {status: 'quoting'}
        let next: QuoteState
        try {
            next = {status: 'quoted', quote: await api.read('orders.create.quote', order)}
        } catch (error) {
            next = error instanceof ActionRefused && error.message !== '' ? {status: 'refused', text: error.message} : {status: 'failed'}
        }
        if (latest.is(ticket)) quote.value = next
    }

    watchDebounced(args, () => void requote(), {debounce: QUOTE_DELAY_MILLIS, deep: true})

    /**
     * Places the order.
     *
     * @returns the new order's id, or null when it was not placed
     */
    async function submit(): Promise<number | null> {
        const order = args.value
        if (order === null) return null
        const result = await place.run('orders.create', order)
        return result?.order ?? null
    }

    return {
        catalogue: computed(() => catalogue.value),
        known: computed(() => known.value),
        status: readonly(status),
        name,
        lines,
        repetition,
        complete: computed(() => args.value !== null),
        quote: readonly(quote),
        placing: place.busy,
        problem: place.problem,
        load,
        addLine,
        removeLine,
        requote,
        submit,
    }
}
