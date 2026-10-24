/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {ActionRefused, gatewayApi, gatewayFailure, type GatewayApi, type WriteAction, type WriteActions} from '~/api/gateway'

/**
 * What went wrong with the last action: the plugin refused it with its own sentence, the server did not answer in
 * time so it may still have happened, or it failed without either.
 */
export type ActionProblem =
    | {kind: 'refused', text: string}
    | {kind: 'uncertain'}
    | {kind: 'failed'}

/** The status the gateway answers with when the server did not answer in time. */
const NO_ANSWER_IN_TIME = 504

/**
 * Runs actions that change something, one at a time. While one runs, `busy` is true and further runs are ignored, so
 * a double tap sends nothing twice. A refusal keeps the plugin's own sentence; a server that did not answer in time is
 * reported as uncertain, since the action may have happened, so the page can say to check before trying again.
 *
 * @param api the gateway's calls, for tests
 * @returns whether an action runs, the last problem, the runner and a reset
 */
export function useAction(api: GatewayApi = gatewayApi()) {
    const busy = ref(false)
    const problem = shallowRef<ActionProblem | null>(null)

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
            problem.value = problemOf(error)
            return null
        } finally {
            busy.value = false
        }
    }

    /** Forgets the last problem, e.g. when the page shows something else. */
    function reset(): void {
        problem.value = null
    }

    return {busy: readonly(busy), problem: readonly(problem), run, reset}
}

/**
 * What an action's failure means for the player.
 *
 * @param error what the call threw
 * @returns the problem
 */
function problemOf(error: unknown): ActionProblem {
    if (error instanceof ActionRefused && error.message !== '') return {kind: 'refused', text: error.message}
    if (gatewayFailure(error).status === NO_ANSWER_IN_TIME) return {kind: 'uncertain'}
    return {kind: 'failed'}
}
