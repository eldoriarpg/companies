/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {gatewayApi, gatewayFailure, type GatewayApi, type PendingSession} from '~/api/gateway'
import type {VersionStatus} from '~/api/generated/protocol'

/** Where the login stands with its waiting session. */
export type LoginStatus = 'loading' | 'waiting' | 'unknown' | 'locked' | 'discontinued' | 'offline'

/** Why the last code was not taken. */
export type LoginProblem = 'wrong' | 'challenge' | 'busy' | 'elsewhere' | 'refused' | 'serverOffline' | 'offline'

/** The letters a code is made of: no I, L, O, 0 or 1, which read alike. */
export const CODE_LETTERS = /^[A-HJKMNP-Z2-9]{6}$/

/**
 * What the reader is told about a code the gateway did not take, by the answer's status and error code: another
 * browser opening the same session meanwhile (409), the plugin refusing the login (403), or the server not
 * answering (503, 504).
 *
 * @param status the HTTP status
 * @param error the gateway's error code
 * @returns the problem
 */
export function problemOf(status: number, error: string | undefined): LoginProblem {
    if (error === 'WRONG_CODE') return 'wrong'
    if (error === 'TURNSTILE_FAILED') return 'challenge'
    if (error === 'RATE_LIMITED' || status === 429) return 'busy'
    if (error === 'SERVER_OFFLINE') return 'serverOffline'
    if (status === 409) return 'elsewhere'
    if (error === 'UNAUTHORIZED') return 'refused'
    return 'offline'
}

/** A short id: six letters and digits. */
export const SHORT_ID = /^[A-Za-z0-9]{6}$/

/**
 * The login of one waiting session: whether it exists and for which server, then the code, with the attempts left and
 * the Turnstile token where the gateway asks for one. A taken code sets the session cookie, and the dashboard opens.
 *
 * @param shortId the short id from the link
 * @param api the gateway's calls, for tests
 * @returns the login's state and its actions
 */
export function useLogin(shortId: string, api: GatewayApi = gatewayApi()) {
    const pending = ref<PendingSession | null>(null)
    const status = ref<LoginStatus>('loading')
    const problem = ref<LoginProblem | null>(null)
    const attemptsLeft = ref(0)
    const sending = ref(false)
    const version = ref<VersionStatus | null>(null)
    const message = ref<string | null>(null)

    /** Asks the gateway for the waiting session. */
    async function load(): Promise<void> {
        status.value = 'loading'
        try {
            const session = await api.pending(shortId)
            pending.value = session
            attemptsLeft.value = session.attemptsLeft
            version.value = session.version
            status.value = session.version.status === 'DISCONTINUED' ? 'discontinued' : 'waiting'
        } catch (error) {
            const {status: code, body} = gatewayFailure(error)
            message.value = body?.message ?? null
            if (body?.error === 'DISCONTINUED') discontinued(body.version)
            else status.value = code === 404 || code === 410 || body?.error === 'UNKNOWN_SESSION' ? 'unknown' : 'offline'
        }
    }

    /**
     * Sends a code.
     *
     * @param code the six letters
     * @param turnstileToken the challenge's token, where the gateway asked for one
     * @returns true when the session is open
     */
    async function submit(code: string, turnstileToken?: string): Promise<boolean> {
        sending.value = true
        problem.value = null
        try {
            await api.login(shortId, {code, turnstileToken})
            return true
        } catch (error) {
            refused(error)
            return false
        } finally {
            sending.value = false
        }
    }

    /**
     * Shows the server as discontinued, with the version state the gateway sent along.
     *
     * @param state the version state, where the answer carried one
     */
    function discontinued(state: VersionStatus | undefined): void {
        version.value = state ?? version.value
        status.value = 'discontinued'
    }

    /**
     * Tells why a code was not taken. The error code decides before the status, since the gateway answers 410 both
     * for a discontinued server and for the last wrong attempt.
     *
     * @param error what the call threw
     */
    function refused(error: unknown): void {
        const {status: code, body} = gatewayFailure(error)
        message.value = body?.message ?? null
        if (body?.attemptsLeft !== undefined) attemptsLeft.value = body.attemptsLeft
        if (body?.error === 'DISCONTINUED') discontinued(body.version)
        else if (body?.error === 'TOO_MANY_ATTEMPTS') status.value = 'locked'
        else if (body?.error === 'UNKNOWN_SESSION') status.value = 'unknown'
        else problem.value = problemOf(code, body?.error)
    }

    return {
        pending: readonly(pending),
        status: readonly(status),
        problem: readonly(problem),
        attemptsLeft: readonly(attemptsLeft),
        sending: readonly(sending),
        version: readonly(version),
        message: readonly(message),
        load,
        submit,
    }
}
