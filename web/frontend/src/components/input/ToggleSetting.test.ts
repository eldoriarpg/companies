/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import CheckboxInput from './CheckboxInput.vue'
import ToggleSetting from './ToggleSetting.vue'

/**
 * A setting is read as a switch named by its label, and a checkbox shows the mixed state it is given.
 *
 * @vitest-environment happy-dom
 */
describe('on and off settings', () => {
    it('flips on a press of the whole row and is named by its label', async () => {
        const wrapper = mount(ToggleSetting, {props: {label: 'Repeat', hint: 'Every week', modelValue: false}})
        const button = wrapper.find('button')
        expect(button.attributes('role')).toBe('switch')
        expect(wrapper.find(`#${button.attributes('aria-labelledby')}`).text()).toBe('Repeat')
        expect(wrapper.find(`#${button.attributes('aria-describedby')}`).text()).toBe('Every week')
        await button.trigger('click')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([true])
    })

    it('shows the mixed state on a checkbox', () => {
        const wrapper = mount(CheckboxInput, {props: {label: 'All', indeterminate: true}})
        expect((wrapper.find('input').element as HTMLInputElement).indeterminate).toBe(true)
    })
})
