/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {mount, type VueWrapper} from '@vue/test-utils'
import {nextTick} from 'vue'
import Popover from './Popover.vue'

let wrapper: VueWrapper | null = null

afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
})

function mountPopover(role: 'menu' | 'dialog') {
    wrapper = mount(Popover, {
        attachTo: document.body,
        props: {label: 'Columns', role, testId: 'panel'},
        slots: {
            trigger: `<template #trigger="{toggle, triggerAttrs}">
                <button data-testid="trigger" v-bind="triggerAttrs" @click.stop="toggle">Open</button>
            </template>`,
            default: '<input type="checkbox" data-testid="inside"/>',
        },
    })
    return wrapper
}

function panel(): HTMLElement | null {
    return document.body.querySelector('[data-testid="panel"]')
}

function insideControl(): HTMLElement {
    return panel()!.querySelector<HTMLElement>('[data-testid="inside"]')!
}

async function openIt() {
    await wrapper!.get('[data-testid="trigger"]').trigger('click')
    await nextTick()
}

function pressOn(target: EventTarget) {
    target.dispatchEvent(new PointerEvent('pointerdown', {bubbles: true}))
}

describe('Popover', () => {
    it('opens its panel at the body when the trigger is pressed', async () => {
        mountPopover('dialog')
        await openIt()

        expect(panel()?.parentElement).toBe(document.body)
    })

    it('closes on a press outside', async () => {
        mountPopover('dialog')
        await openIt()

        pressOn(document.body)
        await nextTick()

        expect(panel()).toBeNull()
    })

    it('closes on a press outside that stops its own click', async () => {
        mountPopover('dialog')
        const elsewhere = document.createElement('div')
        elsewhere.addEventListener('click', event => event.stopPropagation())
        document.body.appendChild(elsewhere)
        await openIt()

        pressOn(elsewhere)
        elsewhere.click()
        await nextTick()

        expect(panel()).toBeNull()
    })

    it('closes on escape', async () => {
        mountPopover('dialog')
        await openIt()

        document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))
        await nextTick()

        expect(panel()).toBeNull()
    })

    it('stays open while a dialog is used', async () => {
        mountPopover('dialog')
        await openIt()
        const inside = insideControl()

        pressOn(inside)
        inside.click()
        await nextTick()

        expect(panel()).not.toBeNull()
    })

    it('closes a menu once something in it was chosen', async () => {
        mountPopover('menu')
        await openIt()

        insideControl().click()
        await nextTick()

        expect(panel()).toBeNull()
    })

    it('stays open when its own trigger is pressed down', async () => {
        mountPopover('dialog')
        await openIt()

        pressOn(wrapper!.get('[data-testid="trigger"]').element)
        await nextTick()

        expect(panel()).not.toBeNull()
    })
})
