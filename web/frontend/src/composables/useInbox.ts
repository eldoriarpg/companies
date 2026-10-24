/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {watchDebounced} from '@vueuse/core'
import {gatewayApi, type GatewayApi, type InboxPage} from '~/api/gateway'

const SEARCH_DELAY_MILLIS = 300

/**
 * The inbox, a page at a time, searched by the plugin. Reading it here marks nothing read; that comes with the
 * inbox's actions.
 *
 * @param api the gateway's calls, for tests
 * @returns the search, the page and its loader
 */
export function useInbox(api: GatewayApi = gatewayApi()) {
    const query = ref('')
    const page = ref(1)
    const result = shallowRef<InboxPage | null>(null)
    const status = ref<'loading' | 'ready' | 'failed'>('loading')

    /** Loads the current page. */
    async function load(): Promise<void> {
        status.value = 'loading'
        try {
            const text = query.value.trim()
            result.value = await api.read('inbox.page', {page: page.value, query: text === '' ? undefined : text})
            status.value = 'ready'
        } catch {
            status.value = 'failed'
        }
    }

    watchDebounced(query, () => {
        page.value = 1
        void load()
    }, {debounce: SEARCH_DELAY_MILLIS})
    watch(page, () => void load())

    return {query, page, result, status: readonly(status), load}
}
