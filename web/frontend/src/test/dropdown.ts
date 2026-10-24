/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {DOMWrapper, flushPromises} from '@vue/test-utils'

/**
 * Driving a dropdown in a component test the way a reader drives it.
 *
 * <p>A dropdown's panel is rendered at the end of the page rather than inside the component, so
 * what is in it is looked for on the whole page. Mount the component with `attachTo: document.body`
 * and unmount it after each test, or one test's open panel is still there in the next.
 */
export function page(): DOMWrapper<Element> {
    return new DOMWrapper(document.body)
}

/** The options of the open panel, in the order it lists them. */
export function options(): DOMWrapper<Element>[] {
    return page().findAll('[role="option"]')
}

/** Whether a panel with a list is open anywhere on the page. */
export function panelIsOpen(): boolean {
    return page().find('[role="listbox"]').exists()
}

/** Presses a key where the focus is, or on the given element, and lets the dropdown answer it. */
export async function press(key: string, target: Element | null = document.activeElement): Promise<void> {
    (target ?? document.body).dispatchEvent(new KeyboardEvent('keydown', {key, bubbles: true, cancelable: true}))
    await flushPromises()
}

/** Presses the pointer down somewhere on the page that belongs to no dropdown. */
export async function pressOutside(): Promise<void> {
    document.body.dispatchEvent(new PointerEvent('pointerdown', {bubbles: true}))
    await flushPromises()
}
