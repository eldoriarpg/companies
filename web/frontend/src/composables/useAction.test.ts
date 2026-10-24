/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
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
        expect(action.problem.value).toBe('You are not part of a company.')
    })

    it('leaves an empty problem for a failure without a sentence', async () => {
        const api = fakeApi()
        api.act = vi.fn().mockRejectedValue(new Error('network')) as unknown as GatewayApi['act']
        const action = useAction(api)

        expect(await action.run('inbox.clear', {})).toBeNull()
        expect(action.problem.value).toBe('')
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
