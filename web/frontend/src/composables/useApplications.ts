/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {type ApplicationsPage, gatewayApi, type GatewayApi} from '~/api/gateway'

/**
 * The open applications to the player's company and the invites it sent, for members who may invite. Loaded again
 * after each answer to one of them, since no patch carries them.
 *
 * @param api the gateway's calls, for tests
 * @returns the applications, their state and the loader
 */
export function useApplications(api: GatewayApi = gatewayApi()) {
    const result = shallowRef<ApplicationsPage | null>(null)
    const status = ref<'loading' | 'ready' | 'failed'>('loading')
    const latest = useLatest()

    /** Loads the applications; an answer overtaken by a newer request is dropped. */
    async function load(): Promise<void> {
        const ticket = latest.next()
        status.value = 'loading'
        try {
            const answer = await api.read('company.applications.list', {})
            if (!latest.is(ticket)) return
            result.value = answer
            status.value = 'ready'
        } catch {
            if (latest.is(ticket)) status.value = 'failed'
        }
    }

    return {result, status: readonly(status), load}
}
