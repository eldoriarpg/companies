/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {nextTick} from 'vue'
import DecimalInput from './DecimalInput.vue'
import NumberInput from './NumberInput.vue'

/**
 * A typed number reaches the model only when it reads as one within its bounds, and a model set from outside
 * rewrites the text without fighting what the player is typing.
 *
 * @vitest-environment happy-dom
 */
describe('numeric fields', () => {
    it('reads an amount with a comma as the decimal mark', async () => {
        const wrapper = mount(DecimalInput, {props: {label: 'Amount', modelValue: undefined}})
        await wrapper.find('input').setValue('1.000,50')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([1000.5])
        expect(wrapper.find('input').attributes('aria-invalid')).toBeUndefined()
    })

    it('holds nothing and shows itself invalid for unreadable text', async () => {
        const wrapper = mount(DecimalInput, {props: {label: 'Amount', modelValue: 5}})
        await wrapper.find('input').setValue('abc')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([undefined])
        expect(wrapper.find('input').attributes('aria-invalid')).toBe('true')
    })

    it('holds nothing outside its bounds', async () => {
        const wrapper = mount(NumberInput, {props: {label: 'Count', modelValue: undefined, min: 1, max: 64}})
        await wrapper.find('input').setValue('65')
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
        expect(wrapper.find('input').attributes('aria-invalid')).toBe('true')
        await wrapper.find('input').setValue('64')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([64])
    })

    it('takes only whole numbers', async () => {
        const wrapper = mount(NumberInput, {props: {label: 'Count', modelValue: undefined}})
        await wrapper.find('input').setValue('2.5')
        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
        expect(wrapper.find('input').attributes('aria-invalid')).toBe('true')
    })

    it('treats an empty field as nothing, not as invalid', async () => {
        const wrapper = mount(NumberInput, {props: {label: 'Count', modelValue: 3}})
        await wrapper.find('input').setValue('')
        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([undefined])
        expect(wrapper.find('input').attributes('aria-invalid')).toBeUndefined()
    })

    it('rewrites the text when the model changes from outside', async () => {
        const wrapper = mount(DecimalInput, {props: {label: 'Amount', modelValue: 20}})
        expect(wrapper.find('input').element.value).toBe('20')
        await wrapper.setProps({modelValue: undefined})
        await nextTick()
        expect(wrapper.find('input').element.value).toBe('')
    })

    it('keeps the text the player typed when it already reads as the model', async () => {
        const wrapper = mount(DecimalInput, {props: {label: 'Amount', modelValue: undefined}})
        await wrapper.find('input').setValue('20,0')
        await wrapper.setProps({modelValue: 20})
        await nextTick()
        expect(wrapper.find('input').element.value).toBe('20,0')
    })

    it('ties the label to the field', () => {
        const wrapper = mount(NumberInput, {props: {label: 'Count', hint: 'Up to a stack'}})
        const input = wrapper.find('input')
        expect(wrapper.find('label').attributes('for')).toBe(input.attributes('id'))
        expect(wrapper.find(`#${input.attributes('aria-describedby')}`).text()).toBe('Up to a stack')
    })

    it('says underneath what it expects', async () => {
        const wrapper = mount(NumberInput, {props: {label: 'Share', modelValue: undefined, max: 25}})
        const input = wrapper.find('input')
        await input.setValue('30')
        expect(wrapper.find(`#${input.attributes('aria-errormessage')}`).text()).toBe('Enter at most 25.')
        await input.setValue('abc')
        expect(wrapper.text()).toContain('Enter a whole number.')
    })
})
