/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import {defineComponent, h, nextTick, ref} from 'vue'
import Modal from './Modal.vue'

enableAutoUnmount(afterEach)

/**
 * Lets the dialog settle: it places its focus a tick after it mounts, and starts listening for
 * presses outside itself a timer after that.
 */
async function settle() {
    await flushPromises()
    await new Promise(resolve => setTimeout(resolve, 0))
    await flushPromises()
}

/** The dialog behind dozens of screens. Its content lands at the end of the body. */
async function mountModal(open: boolean, content = '<p>Content</p>') {
    const wrapper = mount(Modal, {
        props: {modelValue: open},
        slots: {default: content},
        attachTo: document.body,
    })
    await settle()
    return wrapper
}

function dialog(): HTMLElement | null {
    return document.querySelector<HTMLElement>('[role="dialog"]')
}

function overlays(): HTMLElement[] {
    return [...document.querySelectorAll<HTMLElement>('.fixed.inset-0')]
}

function press(target: EventTarget, key: string, init: KeyboardEventInit = {}) {
    target.dispatchEvent(new KeyboardEvent('keydown', {key, bubbles: true, cancelable: true, ...init}))
}

/**
 * A page with a button that opens a dialog through `v-model`, the way the screens open theirs, so
 * the focus has somewhere to come back to.
 */
function mountWithTrigger(content: string) {
    return mount(defineComponent({
        setup() {
            const open = ref(false)
            return () => [
                h('button', {id: 'trigger', onClick: () => open.value = true}, 'Open'),
                h(Modal, {modelValue: open.value, 'onUpdate:modelValue': (v: boolean) => open.value = v}, () => h('div', {innerHTML: content})),
            ]
        },
    }), {attachTo: document.body})
}

describe('Modal', () => {
    it('shows its content when open', async () => {
        await mountModal(true)
        expect(document.body.textContent).toContain('Content')
    })

    it('shows nothing when closed', async () => {
        await mountModal(false)
        expect(document.body.textContent).not.toContain('Content')
    })

    it('reports the close when the close button is used', async () => {
        const wrapper = await mountModal(true)
        dialog()?.querySelector<HTMLButtonElement>('button[data-cancel]')?.click()
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('reports the close when the dimmed page around it is pressed', async () => {
        const wrapper = await mountModal(true)
        overlays()[0]?.dispatchEvent(new PointerEvent('pointerdown', {bubbles: true, button: 0}))
        await settle()
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    /** A toast or a panel at the end of the page is part of the work in the dialog, not a way out of it. */
    it('stays open when something else outside it is pressed', async () => {
        const outside = document.createElement('button')
        document.body.appendChild(outside)
        const wrapper = await mountModal(true)

        outside.dispatchEvent(new PointerEvent('pointerdown', {bubbles: true, button: 0}))
        await settle()

        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
        outside.remove()
    })

    it('reports the close when escape is pressed', async () => {
        const wrapper = await mountModal(true)
        press(document.activeElement ?? document.body, 'Escape')
        expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    /** Anything else that claims the escape, such as an open list inside the dialog, keeps it open. */
    it('stays open when something else inside handled the escape', async () => {
        const wrapper = await mountModal(true, '<input id="field">')
        const field = document.getElementById('field')!
        field.addEventListener('keydown', event => event.preventDefault())

        press(field, 'Escape')

        expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    })

    /**
     * A screen reader announces the dialog by its title, which is the heading its content opens
     * with.
     */
    it('is labelled by the heading of its content', async () => {
        await mountModal(true, '<h3>Cancel order</h3><p>Are you sure?</p>')

        const labelledBy = dialog()?.getAttribute('aria-labelledby')

        expect(labelledBy).toBeTruthy()
        expect(document.getElementById(labelledBy!)?.textContent).toBe('Cancel order')
    })

    it('keeps an id its heading already has', async () => {
        await mountModal(true, '<h2 id="own">Title</h2>')
        expect(dialog()?.getAttribute('aria-labelledby')).toBe('own')
    })

    /**
     * A dialog is answered from the keyboard: the button it is answered with holds the focus when
     * it opens, so the enter key alone is enough.
     */
    it('puts the focus on the button the dialog is answered with', async () => {
        const wrapper = await mountModal(false, '<button data-cancel>Cancel</button><button id="ok">Save</button>')

        await wrapper.setProps({modelValue: true})
        await settle()

        expect(document.activeElement?.id).toBe('ok')
    })

    /**
     * Where a dialog names its button outright, that one wins over the guess, whatever order the
     * buttons happen to be in.
     */
    it('prefers the button a dialog names over the last one', async () => {
        const wrapper = await mountModal(false, '<button id="named" data-confirm>Yes</button><button id="last">No</button>')

        await wrapper.setProps({modelValue: true})
        await settle()

        expect(document.activeElement?.id).toBe('named')
    })

    /** A dialog that is filled in opens with the cursor in the field it marks, ready for typing. */
    it('puts the focus on the field a dialog marks', async () => {
        const wrapper = await mountModal(false, '<input id="name" data-autofocus><button id="ok">Save</button>')

        await wrapper.setProps({modelValue: true})
        await settle()

        expect(document.activeElement?.id).toBe('name')
    })

    it('gives the focus back to what opened it once it closes', async () => {
        mountWithTrigger('<button id="ok">Save</button>')
        const trigger = document.getElementById('trigger') as HTMLButtonElement
        trigger.focus()
        trigger.click()
        await settle()
        expect(dialog()?.contains(document.activeElement)).toBe(true)

        press(document.activeElement!, 'Escape')
        await settle()

        expect(dialog()).toBeNull()
        expect(document.activeElement).toBe(trigger)
    })

    /** Tabbing on from the last control comes round to the first again instead of leaving the dialog. */
    it('keeps the focus inside while tabbing', async () => {
        await mountModal(true, '<input id="first"><button id="last">Save</button>')
        const last = document.getElementById('last') as HTMLElement
        last.focus()

        press(last, 'Tab')
        await nextTick()

        expect(dialog()?.contains(document.activeElement)).toBe(true)
        expect(document.activeElement).not.toBe(last)
    })

    /**
     * Shift and enter answer it from anywhere inside, which is what a text field needs: there the
     * enter key belongs to the field.
     */
    it('answers on shift and enter from inside a text field', async () => {
        let answered = 0
        await mountModal(true, '<input id="text"><button id="ok">Save</button>')
        document.querySelector('#ok')?.addEventListener('click', () => {
            answered++
        })

        press(document.querySelector('#text')!, 'Enter', {shiftKey: true})

        expect(answered, 'the dialog was answered').toBe(1)
    })

    /**
     * The step-up challenge is opened by the API layer over whatever dialog asked for it, and it is
     * mounted for the whole app long before that dialog exists. Opening order has to decide, or the
     * challenge comes up behind and its code field cannot be reached.
     */
    it('puts the dialog opened last in front of the one already open', async () => {
        const first = await mountModal(false, '<p>First</p>')
        const second = await mountModal(false, '<p>Second</p>')

        await first.setProps({modelValue: true})
        await second.setProps({modelValue: true})
        await settle()

        const [firstLayer, secondLayer] = ['First', 'Second']
            .map(text => overlays().find(overlay => overlay.textContent?.includes(text)))
            .map(overlay => Number(overlay?.style.zIndex))
        expect(secondLayer).toBeGreaterThan(firstLayer!)
    })

    it('leaves a plain enter to whatever has the focus', async () => {
        let answered = 0
        await mountModal(true, '<input id="text"><button id="ok">Save</button>')
        document.querySelector('#ok')?.addEventListener('click', () => {
            answered++
        })

        press(document.querySelector('#text')!, 'Enter')

        expect(answered).toBe(0)
    })
})
