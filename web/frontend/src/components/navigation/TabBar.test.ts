/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {afterEach, describe, expect, it} from 'vitest'
import {defineComponent, h, ref} from 'vue'
import {mount, type VueWrapper} from '@vue/test-utils'
import TabBar from './TabBar.vue'

const TABS = [
    {key: 'general', label: 'General'},
    {key: 'members', label: 'Members'},
    {key: 'history', label: 'History'},
]

/**
 * A row of tabs a screen reader announces as one and a keyboard walks with the arrows.
 *
 * @vitest-environment happy-dom
 */
describe('TabBar', () => {
    let wrapper: VueWrapper | null = null

    afterEach(() => {
        wrapper?.unmount()
        wrapper = null
    })

    function mountBar(initial = 'general') {
        const selected = ref(initial)
        wrapper = mount(defineComponent({
            setup: () => () => h(TabBar, {
                tabs: TABS,
                label: 'Company',
                panelId: 'company-panel',
                modelValue: selected.value,
                'onUpdate:modelValue': (key: string) => {
                    selected.value = key
                },
            }),
        }), {attachTo: document.body})
        return selected
    }

    function tabs() {
        return wrapper!.findAll('[role="tab"]')
    }

    function tab(index: number) {
        return tabs()[index]!
    }

    it('is a tab list of tabs', () => {
        mountBar()

        expect(wrapper!.find('[role="tablist"]').exists()).toBe(true)
        expect(tabs().map(each => each.text())).toEqual(['General', 'Members', 'History'])
    })

    it('marks the selected tab and keeps only it in the tab order', () => {
        mountBar('members')

        expect(tabs().map(each => each.attributes('aria-selected'))).toEqual(['false', 'true', 'false'])
        expect(tabs().map(each => each.attributes('tabindex'))).toEqual(['-1', '0', '-1'])
    })

    it('keeps the first tab reachable while none is selected', () => {
        mountBar('unknown')

        expect(tabs().map(each => each.attributes('tabindex'))).toEqual(['0', '-1', '-1'])
    })

    it('selects a tab that is clicked', async () => {
        const selected = mountBar()

        await tab(2).trigger('click')

        expect(selected.value).toBe('history')
        expect(tab(2).attributes('aria-selected')).toBe('true')
    })

    it('moves and selects with the arrow keys, wrapping at either end', async () => {
        const selected = mountBar()

        await tab(0).trigger('keydown', {key: 'ArrowRight'})
        expect(selected.value).toBe('members')
        expect(document.activeElement).toBe(tab(1).element)

        await tab(1).trigger('keydown', {key: 'ArrowLeft'})
        await tab(0).trigger('keydown', {key: 'ArrowLeft'})
        expect(selected.value).toBe('history')
        expect(document.activeElement).toBe(tab(2).element)
    })

    it('jumps to either end with Home and End', async () => {
        const selected = mountBar('members')

        await tab(1).trigger('keydown', {key: 'End'})
        expect(selected.value).toBe('history')

        await tab(2).trigger('keydown', {key: 'Home'})
        expect(selected.value).toBe('general')
        expect(document.activeElement).toBe(tab(0).element)
    })

    it('leaves other keys alone', async () => {
        const selected = mountBar()

        await tab(0).trigger('keydown', {key: 'ArrowDown'})

        expect(selected.value).toBe('general')
    })

    it('names the list and points the selected tab at its panel', () => {
        mountBar('members')
        expect(wrapper!.get('[role="tablist"]').attributes('aria-label')).toBe('Company')
        expect(tabs()[1]?.attributes('aria-controls')).toBe('company-panel')
        expect(tabs()[0]?.attributes('aria-controls')).toBeUndefined()
    })
})
