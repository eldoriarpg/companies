/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {draftOf, offerActions, type OfferForm, prizesOf} from './offers'

/**
 * A goal as the form starts.
 *
 * @returns the form
 */
function form(): OfferForm {
    return {
        kind: 'goal', name: ' Bridge logs ', money: 'minted', itemKind: 'material', item: 'oak_log ', amount: 500, price: 2,
        chunk: 50, open: 4, prizes: '', start: 0, hours: 48, budget: undefined,
    }
}

/**
 * The admin's form of a new event offer.
 *
 * @vitest-environment node
 */
describe('the offer form', () => {
    it('sends a goal with its chunks and no prizes', () => {
        expect(draftOf(form())).toEqual({
            kind: 'goal', name: 'Bridge logs', money: 'minted', material: 'oak_log', amount: 500, price: 2, chunk: 50, open: 4,
            start: 0, hours: 48,
        })
    })

    it('sends a contest with its prizes and criteria and no chunks', () => {
        const draft = draftOf({...form(), kind: 'contest', itemKind: 'criteria', item: '#logs', prizes: '500+250, 100', budget: 9000})

        expect(draft.prizes).toEqual([500, 250, 100])
        expect(draft.criteria).toBe('#logs')
        expect(draft.material).toBeUndefined()
        expect(draft.chunk).toBeUndefined()
        expect(draft.budget).toBe(9000)
    })

    it('reads no prizes from an empty field', () => {
        expect(prizesOf('  ')).toEqual([])
    })

    it('offers the changes the plugin allows in each state', () => {
        expect(offerActions('active', 'goal')).toEqual({pause: true, resume: false, cancel: true, open: true})
        expect(offerActions('paused', 'contest')).toEqual({pause: false, resume: true, cancel: true, open: false})
        expect(offerActions('ended', 'goal')).toEqual({pause: false, resume: false, cancel: false, open: false})
    })
})
