/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {ActionRefused, gatewayApi, type GatewayApi, type WriteAction, type WriteActions} from '~/api/gateway'

/**
 * Runs actions that change something, one at a time. While one runs, `busy` is true and further runs are ignored, so
 * a double tap sends nothing twice. A refusal leaves the plugin's own sentence in `problem`; a failure without one
 * leaves an empty string, for which the page shows its general message.
 *
 * @param api the gateway's calls, for tests
 * @returns whether an action runs, the last problem and the runner
 */
export function useAction(api: GatewayApi = gatewayApi()) {
    const busy = ref(false)
    const problem = ref<string | null>(null)

    /**
     * Runs an action.
     *
     * @param name the action
     * @param args its arguments
     * @returns its answer, or null when it was refused, failed or another one was running
     */
    async function run<N extends WriteAction>(name: N, args: WriteActions[N]['args']): Promise<WriteActions[N]['result'] | null> {
        if (busy.value) return null
        busy.value = true
        problem.value = null
        try {
            return await api.act(name, args)
        } catch (error) {
            problem.value = error instanceof ActionRefused ? error.message : ''
            return null
        } finally {
            busy.value = false
        }
    }

    return {busy: readonly(busy), problem: readonly(problem), run}
}
