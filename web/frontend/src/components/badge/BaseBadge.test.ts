/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import BaseBadge from './BaseBadge.vue'
import CountBadge from './CountBadge.vue'
import ErrorBadge from './ErrorBadge.vue'

/**
 * A badge carries the colours its variant names on the shared square.
 *
 * @vitest-environment happy-dom
 */
describe('BaseBadge', () => {
    it('paints the box and the text it is given', () => {
        const badge = mount(BaseBadge, {props: {boxClass: 'bg-surface', textClass: 'text-gain'}, slots: {default: 'Delivered'}})

        expect(badge.classes()).toEqual(expect.arrayContaining(['bg-surface', 'text-gain']))
        expect(badge.text()).toBe('Delivered')
    })

    it('reads in the body text colour unless told otherwise', () => {
        expect(mount(BaseBadge, {props: {boxClass: 'bg-surface'}}).classes()).toContain('text-ink')
    })

    it('shows a problem in the problem colours', () => {
        expect(mount(ErrorBadge).classes()).toEqual(expect.arrayContaining(['bg-problem', 'text-problem-ink']))
    })

    it('shows a count', () => {
        expect(mount(CountBadge, {props: {count: 3}}).text()).toBe('3')
    })
})
