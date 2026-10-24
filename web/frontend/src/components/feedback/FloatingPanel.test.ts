/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import {defineComponent, h, nextTick, ref} from 'vue'
import FloatingPanel from './FloatingPanel.vue'
import Modal from './Modal.vue'

enableAutoUnmount(afterEach)

afterEach(() => {
    vi.restoreAllMocks()
})

/** The trigger stands at a known place, so where the panel lands can be read off. */
function anchorAt(rect: {top: number; bottom: number; left: number; right: number}) {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
        ...rect, x: rect.left, y: rect.top, width: rect.right - rect.left, height: rect.bottom - rect.top, toJSON: () => ({}),
    })
}

/** Lets the panel's focus scope stand, which happens a tick after it mounts. */
async function settle() {
    await flushPromises()
    await new Promise(resolve => setTimeout(resolve, 0))
    await flushPromises()
}

function mountPanel(props: Record<string, unknown> = {}) {
    return mount(FloatingPanel, {
        attachTo: document.body,
        props: {open: false, label: 'Colour', role: 'dialog', testId: 'panel', ...props},
        slots: {
            trigger: '<button data-testid="trigger">Colour</button>',
            default: '<input data-testid="inside"/>',
        },
    })
}

async function opened(props: Record<string, unknown> = {}) {
    const wrapper = mountPanel(props)
    await wrapper.setProps({open: true})
    await settle()
    return wrapper
}

function panel(): HTMLElement | null {
    return document.querySelector<HTMLElement>('[data-testid="panel"]')
}

function pressOn(target: EventTarget) {
    target.dispatchEvent(new PointerEvent('pointerdown', {bubbles: true, button: 0}))
}

describe('FloatingPanel', () => {
    it('renders nothing while closed', () => {
        mountPanel()

        expect(panel()).toBeNull()
    })

    it('renders its panel at the body, placed under the trigger', async () => {
        anchorAt({top: 100, bottom: 120, left: 30, right: 60})
        const wrapper = await opened()

        expect(panel()?.parentElement).toBe(document.body)
        expect(panel()?.style.position).toBe('fixed')
        expect(panel()?.style.top).toBe('124px')
        expect(panel()?.style.left).toBe('60px')
        expect(panel()?.style.opacity).toBe('1')
        expect(wrapper.emitted('opened')?.[0]).toEqual([panel()])
    })

    it('lines up with the left edge of the trigger when told to', async () => {
        anchorAt({top: 100, bottom: 120, left: 30, right: 60})
        await opened({align: 'start'})

        expect(panel()?.style.left).toBe('30px')
    })

    it('ties the trigger to the panel', async () => {
        const wrapper = mount(FloatingPanel, {
            props: {open: false, label: 'Colour', role: 'menu'},
            slots: {trigger: `<template #trigger="{triggerAttrs}"><button v-bind="triggerAttrs">Colour</button></template>`},
        })

        expect(wrapper.get('button').attributes('aria-expanded')).toBe('false')
        expect(wrapper.get('button').attributes('aria-haspopup')).toBe('menu')
    })

    it('stays open on a press inside the panel and on its trigger', async () => {
        const wrapper = await opened()

        pressOn(panel()!.querySelector('[data-testid="inside"]')!)
        pressOn(wrapper.get('[data-testid="trigger"]').element)
        await nextTick()

        expect(panel()).not.toBeNull()
        expect(wrapper.emitted('update:open')).toBeUndefined()
    })

    it('closes on a press outside', async () => {
        const wrapper = await opened()

        pressOn(document.body)
        await nextTick()

        expect(wrapper.emitted('update:open')).toEqual([[false]])
        expect(wrapper.emitted('dismissed')).toEqual([['outside']])
    })

    it('closes on escape', async () => {
        const wrapper = await opened()

        document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))
        await nextTick()

        expect(wrapper.emitted('update:open')).toEqual([[false]])
        expect(wrapper.emitted('dismissed')).toEqual([['escape']])
    })
})

describe('FloatingPanel inside a dialog', () => {
    /** A dialog holding a field and a panel, the way an order form holds its item picker. */
    async function mountInDialog() {
        const dialogOpen = ref(true)
        const panelOpen = ref(false)
        const wrapper = mount(defineComponent({
            setup: () => () => h(Modal, {
                'modelValue': dialogOpen.value,
                'onUpdate:modelValue': (value: boolean) => dialogOpen.value = value,
            }, () => [
                h('input', {'data-testid': 'field'}),
                h(FloatingPanel, {
                    'open': panelOpen.value,
                    'onUpdate:open': (value: boolean) => panelOpen.value = value,
                    'label': 'Colour',
                    'role': 'dialog',
                    'testId': 'panel',
                }, {
                    trigger: () => h('button', {'data-testid': 'trigger'}, 'Colour'),
                    default: () => h('input', {'data-testid': 'inside'}),
                }),
            ]),
        }), {attachTo: document.body})
        await settle()
        panelOpen.value = true
        await settle()
        return {wrapper, dialogOpen, panelOpen}
    }

    function dialog(): HTMLElement {
        return document.querySelector<HTMLElement>('[role="dialog"]:not([data-testid="panel"])')!
    }

    it('paints above the dialog and takes the pointer the dialog took from the page', async () => {
        await mountInDialog()
        const layer = Number(dialog().parentElement!.style.zIndex)

        expect(dialog().contains(panel())).toBe(false)
        expect(panel()?.classList).toContain('z-[90]')
        expect(layer).toBeLessThan(90)
        expect(panel()?.classList).toContain('pointer-events-auto')
    })

    it('lets a field in the panel take the focus while the dialog holds it otherwise', async () => {
        await mountInDialog()
        const inside = panel()!.querySelector<HTMLInputElement>('[data-testid="inside"]')!

        inside.focus()
        await settle()

        expect(document.activeElement).toBe(inside)
    })

    it('keeps the dialog and itself open on a press inside the panel', async () => {
        const {dialogOpen, panelOpen} = await mountInDialog()

        pressOn(panel()!.querySelector('[data-testid="inside"]')!)
        await settle()

        expect(dialogOpen.value).toBe(true)
        expect(panelOpen.value).toBe(true)
    })

    it('closes itself but not the dialog on escape', async () => {
        const {dialogOpen, panelOpen} = await mountInDialog()

        panel()!.querySelector<HTMLInputElement>('[data-testid="inside"]')!
            .dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true, cancelable: true}))
        await settle()

        expect(panelOpen.value).toBe(false)
        expect(dialogOpen.value).toBe(true)
    })

    it('closes on a press elsewhere in the dialog, which stays open', async () => {
        const {dialogOpen, panelOpen} = await mountInDialog()

        pressOn(dialog().querySelector('[data-testid="field"]')!)
        await settle()

        expect(panelOpen.value).toBe(false)
        expect(dialogOpen.value).toBe(true)
    })
})
