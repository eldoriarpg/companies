/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {watchDebounced} from '@vueuse/core'
import {gatewayApi, type GatewayApi, type InboxPage} from '~/api/gateway'

const SEARCH_DELAY_MILLIS = 300

/**
 * The inbox, a page at a time, searched by the plugin, with the categories the player muted. Reading it marks nothing
 * read; marking read, clearing and muting are the inbox's actions.
 *
 * @param api the gateway's calls, for tests
 * @returns the search, the page and its loader
 */
export function useInbox(api: GatewayApi = gatewayApi()) {
    const query = ref('')
    const page = ref(1)
    const result = shallowRef<InboxPage | null>(null)
    const status = ref<'loading' | 'ready' | 'failed'>('loading')
    const latest = useLatest()

    /** Loads the current page; an answer overtaken by a newer request is dropped. */
    async function load(): Promise<void> {
        const ticket = latest.next()
        status.value = 'loading'
        try {
            const text = query.value.trim()
            const answer = await api.read('inbox.page', {page: page.value, query: text === '' ? undefined : text})
            if (!latest.is(ticket)) return
            result.value = answer
            status.value = 'ready'
        } catch {
            if (latest.is(ticket)) status.value = 'failed'
        }
    }

    watchDebounced(query, () => {
        page.value = 1
        void load()
    }, {debounce: SEARCH_DELAY_MILLIS})
    watch(page, () => void load())

    return {query, page, result, status: readonly(status), load}
}
