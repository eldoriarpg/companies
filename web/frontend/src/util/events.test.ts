/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {closedState, joinable, offerMaterial, offerTime} from './events'

const NOW = new Date('2026-10-29T12:00:00Z')

/**
 * The time and the buttons of an event offer on the Events page.
 *
 * @vitest-environment node
 */
describe('event offers', () => {
    it('counts down to the start of an offer waiting for it, to the end of every other one', () => {
        expect(offerTime({state: 'scheduled', starts: '2026-10-29T14:30:00Z', ends: '2026-10-30T14:30:00Z'}, NOW)).toEqual({key: 'starts', hours: 3})
        expect(offerTime({state: 'active', starts: '2026-10-29T10:00:00Z', ends: '2026-10-29T17:00:00Z'}, NOW)).toEqual({key: 'ends', hours: 5})
        expect(offerTime({state: 'scheduled', starts: '2026-10-29T11:00:00Z', ends: '2026-10-29T13:00:00Z'}, NOW)).toEqual({key: 'ends', hours: 1})
    })

    it('says a closed offer closed instead of counting down', () => {
        expect(offerTime({state: 'ended', starts: '2026-10-28T10:00:00Z', ends: '2026-10-30T10:00:00Z'}, NOW)).toEqual({key: 'closed', hours: 0})
        expect(closedState('cancelled')).toBe(true)
        expect(closedState('paused')).toBe(false)
    })

    it('offers joining only running contests to members who manage orders', () => {
        expect(joinable({kind: 'contest', state: 'active'}, true)).toBe(true)
        expect(joinable({kind: 'contest', state: 'scheduled'}, true)).toBe(false)
        expect(joinable({kind: 'goal', state: 'active'}, true)).toBe(false)
        expect(joinable({kind: 'contest', state: 'active'}, false)).toBe(false)
        expect(joinable({kind: 'contest', state: 'ended'}, true)).toBe(false)
    })

    it('marks criteria with a neutral item', () => {
        expect(offerMaterial('oak_log')).toBe('minecraft:oak_log')
        expect(offerMaterial(null)).toBe('minecraft:paper')
    })
})
