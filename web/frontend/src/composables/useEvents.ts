/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {gatewayApi, type EventDetails, type EventList, type GatewayApi} from '~/api/gateway'

/**
 * The Events page: the event offers the plugin shows a player, and one of them with its contributors or its ranking.
 * Joining or leaving a contest answers the contest as it is afterwards, which replaces the details and the row.
 *
 * @param api the gateway's calls, for tests
 * @returns the list, the details and their loading
 */
export function useEvents(api: GatewayApi = gatewayApi()) {
    const list = shallowRef<EventList | null>(null)
    const status = ref<'loading' | 'ready' | 'failed'>('loading')
    const detail = shallowRef<EventDetails | null>(null)
    const detailStatus = ref<'idle' | 'loading' | 'ready' | 'failed'>('idle')
    const latest = useLatest()

    /** Loads the offers. */
    async function load(): Promise<void> {
        status.value = 'loading'
        try {
            list.value = await api.read('events.list', {})
            status.value = 'ready'
        } catch {
            status.value = 'failed'
        }
    }

    /**
     * Loads one offer; an answer overtaken by another choice is dropped.
     *
     * @param id the offer's id, none to close it
     */
    async function open(id: number | null): Promise<void> {
        const ticket = latest.next()
        if (id === null) {
            detail.value = null
            detailStatus.value = 'idle'
            return
        }
        detailStatus.value = 'loading'
        try {
            const found = await api.read('events.info', {offer: id})
            if (!latest.is(ticket)) return
            detail.value = found
            detailStatus.value = 'ready'
        } catch {
            if (!latest.is(ticket)) return
            detail.value = null
            detailStatus.value = 'failed'
        }
    }

    /**
     * Shows an offer as an action answered it, in the details and in the list.
     *
     * @param changed the offer afterwards
     */
    function changed(changed: EventDetails): void {
        detail.value = changed
        const current = list.value
        if (current) list.value = {...current, offers: current.offers.map(row => (row.id === changed.offer.id ? changed.offer : row))}
    }

    /**
     * Reads an offer again after the plugin refused a change of it, as the page may have shown it out of date; the
     * refusal already dropped the gateway's cached answers, so the plugin answers as it is now.
     *
     * @param id the offer's id
     */
    async function refresh(id: number): Promise<void> {
        await open(id)
        if (detail.value?.offer.id === id) changed(detail.value)
    }

    return {list, status: readonly(status), detail, detailStatus: readonly(detailStatus), load, open, changed, refresh}
}
