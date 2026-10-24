/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {linePoints} from './chart'
import {initials, matches, swatchOf} from './text'

/** Searching lists, marks for names and the points of a chart. */
describe('text and charts', () => {
    it('matches every word in any text, regardless of case', () => {
        expect(matches('', 'anything')).toBe(true)
        expect(matches('spruce market', 'Market stalls', 'Spruce Planks ×960')).toBe(true)
        expect(matches('oak', 'Market stalls', undefined)).toBe(false)
    })

    it('takes two letters for a name', () => {
        expect(initials('Mossvale SMP')).toBe('MS')
        expect(initials('Kestrel_')).toBe('KE')
        expect(initials('§a')).toBe('A')
        expect(initials('')).toBe('?')
    })

    it('gives a material the same colour every time', () => {
        expect(swatchOf('minecraft:diamond')).toBe(swatchOf('minecraft:diamond'))
        expect(swatchOf('minecraft:diamond')).not.toBe(swatchOf('minecraft:oak_log'))
    })

    it('spreads values over the chart', () => {
        expect(linePoints([1, 2, 3], 100, 20)).toBe('0.0,18.0 50.0,10.0 100.0,2.0')
        expect(linePoints([5], 100, 20)).toBe('0.0,18.0 100.0,18.0')
        expect(linePoints([], 100, 20)).toBe('')
    })
})
