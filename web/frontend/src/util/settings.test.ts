/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {changedValue, settingProblem} from './settings'

const t = (key: string, values?: Record<string, unknown>): string => `${key}${values ? JSON.stringify(values) : ''}`

describe('settingProblem', () => {
    it('names only the lower bound when there is no upper one', () => {
        expect(settingProblem(t, {min: 0, max: null}, 'range')).toBe('admin.settings.problem.atLeast{"min":0}')
    })

    it('names both bounds when there is an upper one', () => {
        expect(settingProblem(t, {min: 1, max: 50}, 'range')).toBe('admin.settings.problem.range{"min":1,"max":50}')
    })

    it('passes other reasons through', () => {
        expect(settingProblem(t, {}, 'whole')).toBe('admin.settings.problem.whole')
    })
})

describe('changedValue', () => {
    it('compares values as sent', () => {
        expect(changedValue([1, 2], [1, 2])).toBe(false)
        expect(changedValue(5, 6)).toBe(true)
        expect(changedValue(undefined, undefined)).toBe(false)
    })
})
