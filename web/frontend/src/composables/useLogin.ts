/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {gatewayApi, gatewayFailure, type GatewayApi, type PendingSession} from '~/api/gateway'
import type {VersionStatus} from '~/api/generated/protocol'

/** Where the login stands with its waiting session. */
export type LoginStatus = 'loading' | 'waiting' | 'unknown' | 'discontinued' | 'offline'

/** Why the last code was not taken. */
export type LoginProblem = 'wrong' | 'locked' | 'challenge' | 'busy' | 'offline'

/** The letters a code is made of: no I, L, O, 0 or 1, which read alike. */
export const CODE_LETTERS = /^[A-HJKMNP-Z2-9]{6}$/

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
            version.value = body?.version ?? null
            if (body?.error === 'DISCONTINUED') status.value = 'discontinued'
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
     * Tells why a code was not taken.
     *
     * @param error what the call threw
     */
    function refused(error: unknown): void {
        const {status: code, body} = gatewayFailure(error)
        if (body?.attemptsLeft !== undefined) attemptsLeft.value = body.attemptsLeft
        if (body?.error === 'WRONG_CODE') problem.value = 'wrong'
        else if (body?.error === 'TURNSTILE_FAILED') problem.value = 'challenge'
        else if (body?.error === 'RATE_LIMITED' || code === 429) problem.value = 'busy'
        else if (body?.error === 'TOO_MANY_ATTEMPTS' || body?.error === 'UNKNOWN_SESSION' || code === 410) {
            problem.value = 'locked'
            status.value = 'unknown'
        } else {
            problem.value = 'offline'
        }
    }

    return {
        pending: readonly(pending),
        status: readonly(status),
        problem: readonly(problem),
        attemptsLeft: readonly(attemptsLeft),
        sending: readonly(sending),
        version: readonly(version),
        load,
        submit,
    }
}
