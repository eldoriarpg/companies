/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {linePoints, lineSegments} from './chart'

/** The lines of a chart. */
describe('a chart with gaps', () => {
    it('draws one line when no value is missing', () => {
        expect(lineSegments([1, 2, 3], 100, 20)).toEqual([linePoints([1, 2, 3], 100, 20)])
    })

    it('keeps every value at its place and splits the line at the gaps', () => {
        const segments = lineSegments([1, 2, null, 3, null], 100, 20)

        expect(segments).toHaveLength(2)
        expect(segments[0]).toBe('0.0,18.0 25.0,10.0')
        expect(segments[1]).toBe('73.5,2.0 76.5,2.0')
    })

    it('draws nothing without a value', () => {
        expect(lineSegments([null, null], 100, 20)).toEqual([])
    })
})
