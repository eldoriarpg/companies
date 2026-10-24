/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import type {PendingSession} from '~/api/gateway'
import {fakeApi, gatewayError} from '~/test/fakes'
import {problemOf, useLogin} from './useLogin'

/**
 * A waiting session of the tests.
 *
 * @param change what differs
 * @returns the session
 */
function pending(change: Partial<PendingSession> = {}): PendingSession {
    return {server: {name: 'Mossvale SMP'}, expiresAt: '2026-10-24T12:05:00Z', attemptsLeft: 5, version: {status: 'SUPPORTED'}, ...change}
}

/**
 * A login whose code the gateway answers with an error.
 *
 * @param status the HTTP status
 * @param body the error body
 * @returns the login after the code was sent
 */
async function refusedWith(status: number, body: object) {
    const api = fakeApi()
    api.pending.mockResolvedValue(pending())
    api.login.mockRejectedValue(gatewayError(status, body))
    const login = useLogin('MOSS01', api)
    await login.load()
    expect(await login.submit('ABCDEF')).toBe(false)
    return login
}

/** Logging in with the code from the chat. */
describe('the login', () => {
    it('finds the waiting session and opens it with the right code', async () => {
        const api = fakeApi()
        api.pending.mockResolvedValue(pending())
        api.login.mockResolvedValue(undefined)
        const login = useLogin('MOSS01', api)
        await login.load()
        expect(login.status.value).toBe('waiting')
        expect(await login.submit('KXT4PR', 'token')).toBe(true)
        expect(api.login).toHaveBeenCalledWith('MOSS01', {code: 'KXT4PR', turnstileToken: 'token'})
    })

    it('counts down the attempts on a wrong code', async () => {
        const login = await refusedWith(401, {error: 'WRONG_CODE', attemptsLeft: 4})
        expect(login.problem.value).toBe('wrong')
        expect(login.attemptsLeft.value).toBe(4)
    })

    it('reads the error code before the status of a 410', async () => {
        const discontinued = await refusedWith(410, {error: 'DISCONTINUED', message: 'Update to 2.4.', version: {status: 'DISCONTINUED'}})
        expect(discontinued.status.value).toBe('discontinued')
        expect(discontinued.message.value).toBe('Update to 2.4.')
        expect(discontinued.version.value?.status).toBe('DISCONTINUED')
        const locked = await refusedWith(410, {error: 'TOO_MANY_ATTEMPTS'})
        expect(locked.status.value).toBe('locked')
    })

    it('tells another browser, a refusing plugin and an offline server apart', async () => {
        expect((await refusedWith(409, {error: 'INTERNAL'})).problem.value).toBe('elsewhere')
        const refused = await refusedWith(403, {error: 'UNAUTHORIZED', message: 'You may not use the dashboard.'})
        expect(refused.problem.value).toBe('refused')
        expect(refused.message.value).toBe('You may not use the dashboard.')
        expect((await refusedWith(403, {error: 'TURNSTILE_FAILED'})).problem.value).toBe('challenge')
        expect((await refusedWith(503, {error: 'SERVER_OFFLINE'})).problem.value).toBe('serverOffline')
        expect((await refusedWith(504, {error: 'SERVER_OFFLINE'})).problem.value).toBe('serverOffline')
    })

    it('shows a server discontinued already when the session is looked up', async () => {
        const api = fakeApi()
        api.pending.mockRejectedValue(gatewayError(410, {error: 'DISCONTINUED', message: 'Gone.'}))
        const login = useLogin('GONE01', api)
        await login.load()
        expect(login.status.value).toBe('discontinued')
        expect(login.message.value).toBe('Gone.')
    })

    it('says when the session does not exist', async () => {
        const api = fakeApi()
        api.pending.mockRejectedValue(gatewayError(404, {error: 'UNKNOWN_SESSION'}))
        const login = useLogin('NOPE00', api)
        await login.load()
        expect(login.status.value).toBe('unknown')
    })

    it('names a problem by status where the error code is generic', () => {
        expect(problemOf(429, undefined)).toBe('busy')
        expect(problemOf(502, 'INTERNAL')).toBe('offline')
        expect(problemOf(0, undefined)).toBe('offline')
    })
})
