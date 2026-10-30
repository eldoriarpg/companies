/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {closerKind, eventDraftOf, serverEventActions, type ServerEventForm} from './serverEvents'

/**
 * A reward multiplier as an admin fills the form.
 *
 * @returns the form
 */
function form(): ServerEventForm {
    return {
        name: ' Harvest ', kind: 'reward_multiplier', value: '1.5 ', materials: 'wheat, carrot', levels: '',
        start: 'now', end: '2d', funding: 'MINTED', account: 'bank', budget: '500',
    }
}

/**
 * The admin's server events.
 *
 * @vitest-environment node
 */
describe('the server event form', () => {
    it('sends a minted multiplier with its budget and no account', () => {
        expect(eventDraftOf(form())).toEqual({
            name: 'Harvest', kind: 'REWARD_MULTIPLIER', value: '1.5', materials: 'wheat, carrot', start: 'now', end: '2d',
            funding: 'MINTED', budget: '500',
        })
    })

    it('leaves the money and the levels of a fee reduction out', () => {
        const draft = eventDraftOf({...form(), kind: 'fee_reduction', value: '50', levels: '2,3'})

        expect(draft.funding).toBeUndefined()
        expect(draft.budget).toBeUndefined()
        expect(draft.levels).toBeUndefined()
    })

    it('names the account of a multiplier paid from one', () => {
        expect(eventDraftOf({...form(), funding: 'ACCOUNT'}).account).toBe('bank')
    })
})

describe('the changes of a server event', () => {
    it('ends only a running event and changes the budget only of a multiplier', () => {
        expect(serverEventActions('scheduled', 'tax_reduction')).toEqual({end: false, cancel: true, extend: true, budget: false})
        expect(serverEventActions('active', 'reward_multiplier')).toEqual({end: true, cancel: true, extend: true, budget: true})
        expect(serverEventActions('ended', 'reward_multiplier')).toEqual({end: false, cancel: false, extend: false, budget: false})
    })

    it('names the markers of who closed an event', () => {
        expect(closerKind(null)).toBe('time')
        expect(closerKind('#missed')).toBe('missed')
        expect(closerKind('#template')).toBe('template')
        expect(closerKind('Ada@dashboard')).toBe('admin')
    })
})
