/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {arrow, compact, count, hoursLeft, money, moneyChange, percentChange, trendOf, when} from './format'

/** How numbers, changes and times read on the dashboard. */
describe('format', () => {
    it('writes money with two decimals and grouping', () => {
        expect(money(18450, 'en')).toBe('18,450.00')
        expect(money(18450, 'de')).toBe('18.450,00')
    })

    it('writes counts and short counts', () => {
        expect(count(1728, 'en')).toBe('1,728')
        expect(compact(9400, 'en')).toBe('9.4K')
    })

    it('marks gains, losses and no change', () => {
        expect(trendOf(4.1)).toBe('up')
        expect(trendOf(-2)).toBe('down')
        expect(trendOf(0.01)).toBe('flat')
        expect(arrow('flat')).toBe('')
        expect(percentChange(4.1, 'en')).toBe('▲4.1%')
        expect(percentChange(-5.3, 'en')).toBe('▼5.3%')
        expect(percentChange(0, 'en')).toBe('0.0%')
    })

    it('writes a change of money with its share', () => {
        expect(moneyChange(1240, 17210, 'en')).toBe('▲ 1,240.00 (7.2%)')
        expect(moneyChange(-50, 0, 'en')).toBe('▼ 50.00 (0.0%)')
    })

    it('counts the hours left, rounded up and never negative', () => {
        const now = new Date('2026-10-24T10:00:00Z')
        expect(hoursLeft('2026-10-24T23:30:00Z', now)).toBe(14)
        expect(hoursLeft('2026-10-24T09:00:00Z', now)).toBe(0)
    })

    it('writes a moment by how long ago it was', () => {
        const now = new Date(2026, 9, 24, 13, 0)
        expect(when(new Date(2026, 9, 24, 12, 41).toISOString(), now, 'en')).toBe('12:41')
        expect(when(new Date(2026, 9, 22, 12, 41).toISOString(), now, 'en')).toBe('Thu')
        expect(when(new Date(2026, 8, 2, 12, 41).toISOString(), now, 'en')).toBe('Sep 2')
    })
})
