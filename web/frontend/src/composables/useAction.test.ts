/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {AxiosError, AxiosHeaders} from 'axios'
import {describe, expect, it, vi} from 'vitest'
import {ActionRefused, type GatewayApi} from '~/api/gateway'
import {fakeApi} from '~/test/fakes'
import {useAction} from './useAction'

/** Running an action that changes something. */
describe('an action', () => {
    it('answers with the data and clears the problem', async () => {
        const api = fakeApi()
        api.act = vi.fn().mockResolvedValue({order: 4}) as unknown as GatewayApi['act']
        const action = useAction(api)

        expect(await action.run('orders.accept', {order: 4})).toEqual({order: 4})
        expect(action.problem.value).toBeNull()
        expect(action.busy.value).toBe(false)
    })

    it('keeps the plugin\'s sentence of a refusal', async () => {
        const api = fakeApi()
        api.act = vi.fn().mockRejectedValue(new ActionRefused('error.noMember', 'You are not part of a company.')) as unknown as GatewayApi['act']
        const action = useAction(api)

        expect(await action.run('orders.accept', {order: 4})).toBeNull()
        expect(action.problem.value).toEqual({kind: 'refused', text: 'You are not part of a company.'})
    })

    it('reports a failure without a sentence as failed, until it is reset', async () => {
        const api = fakeApi()
        api.act = vi.fn().mockRejectedValue(new Error('network')) as unknown as GatewayApi['act']
        const action = useAction(api)

        expect(await action.run('inbox.clear', {})).toBeNull()
        expect(action.problem.value).toEqual({kind: 'failed', cause: 'OFFLINE'})
        action.reset()
        expect(action.problem.value).toBeNull()
    })

    it('reports a server that did not answer in time as uncertain', async () => {
        const api = fakeApi()
        const timeout = new AxiosError('timeout', 'ERR_BAD_RESPONSE', undefined, undefined, {
            status: 504, statusText: 'Gateway Timeout', data: {error: 'SERVER_OFFLINE'}, headers: {}, config: {headers: new AxiosHeaders()},
        })
        api.act = vi.fn().mockRejectedValue(timeout) as unknown as GatewayApi['act']
        const action = useAction(api)

        expect(await action.run('orders.own.cancel', {order: 4})).toBeNull()
        expect(action.problem.value).toEqual({kind: 'uncertain'})
    })

    it('sends nothing twice while one runs', async () => {
        const api = fakeApi()
        let finish: (value: unknown) => void = () => {}
        const act = vi.fn().mockReturnValue(new Promise(resolve => {
            finish = resolve
        }))
        api.act = act as unknown as GatewayApi['act']
        const action = useAction(api)

        const first = action.run('inbox.read-all', {})
        expect(await action.run('inbox.read-all', {})).toBeNull()
        finish({marked: 2})
        expect(await first).toEqual({marked: 2})
        expect(act).toHaveBeenCalledTimes(1)
    })
})
