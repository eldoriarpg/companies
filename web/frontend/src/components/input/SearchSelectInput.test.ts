/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import SearchSelectInput from './SearchSelectInput.vue'
import {options, page, panelIsOpen, press, pressOutside} from '~/test/dropdown'

enableAutoUnmount(afterEach)

const ITEMS = [
    {value: 'minecraft:stone', label: 'Stone', group: 'Materials'},
    {value: 'minecraft:oak_log', label: 'Oak Log', group: 'Materials'},
    {value: 'saved:1', label: 'Sharp blade', group: 'Saved items'},
]

/**
 * Opens a picker over the items.
 *
 * @param props what the picker gets besides its options
 * @returns the mounted picker, open
 */
async function openPicker(props: Record<string, unknown> = {}) {
    const wrapper = mount(SearchSelectInput, {
        props: {modelValue: '', label: 'Item', options: ITEMS, ...props},
        attachTo: document.body,
    })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    return wrapper
}

/**
 * Finds an item by typing and takes it with the keyboard; a long list shows its first entries and says so.
 *
 * @vitest-environment happy-dom
 */
describe('SearchSelectInput', () => {
    it('reads the current choice, or the value where it is not on offer', () => {
        expect(mount(SearchSelectInput, {props: {modelValue: 'saved:1', label: 'Item', options: ITEMS}}).get('button').text()).toBe('Sharp blade')
        expect(mount(SearchSelectInput, {props: {modelValue: 'minecraft:dirt', label: 'Item', options: ITEMS}}).get('button').text()).toBe('minecraft:dirt')
    })

    it('finds by label and by value, and takes the first match on Enter', async () => {
        const wrapper = await openPicker()
        await page().get('input').setValue('oak_log')
        expect(options().map(option => option.text())).toEqual(['Oak Log'])
        await press('Enter')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual(['minecraft:oak_log'])
        expect(panelIsOpen()).toBe(false)
    })

    it('shows the entries under their headings', async () => {
        await openPicker()
        expect(page().text()).toContain('Saved items')
    })

    it('says so when nothing matches', async () => {
        await openPicker()
        await page().get('input').setValue('xyz')
        expect(page().text()).toContain('Nothing matches the search.')
    })

    it('shows only the first entries of a long list and says how to narrow it', async () => {
        await openPicker({limit: 2})
        expect(options()).toHaveLength(2)
        expect(page().text()).toContain('Showing the first 2 of 3.')
    })

    it('closes on a press outside without taking anything', async () => {
        const wrapper = await openPicker()
        await pressOutside()
        expect(panelIsOpen()).toBe(false)
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })

    it('ties its label to the field', () => {
        const wrapper = mount(SearchSelectInput, {props: {modelValue: '', label: 'Item', options: ITEMS}})
        expect(wrapper.get('label').attributes('for')).toBe(wrapper.get('button').attributes('id'))
    })
})
