/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {useEventListener} from '@vueuse/core'
import type {Ref} from 'vue'

/** How a panel was sent away: by a press somewhere else on the page, or by the escape key. */
export type DismissedBy = 'outside' | 'escape'

/**
 * Closes a panel when the reader presses somewhere outside it or presses escape.
 *
 * <p>It listens for the pointer going down, not for a click. Plenty of the page stops its clicks
 * from travelling on (a row menu's trigger, a header's filter icon, the cells of a clickable row),
 * and a panel waiting for a click to reach the document would stay open over every one of them.
 * The pointer going down reaches the document before any of those handlers run.
 *
 * <p>The listeners are only there while the panel is open, so a page full of closed menus costs
 * nothing. A closed panel never touches the document at all, which is what lets a page holding one
 * be rendered on the server, where there is no document.
 *
 * @param open      whether the panel is showing
 * @param regions   the elements that count as inside: usually the trigger and the panel, which is
 *                  often rendered elsewhere in the document than its trigger
 * @param onDismiss called once per press outside or escape while open
 */
export function useDismiss(
    open: Ref<boolean>,
    regions: () => (HTMLElement | null | undefined)[],
    onDismiss: (by: DismissedBy) => void,
) {
    function isInside(target: EventTarget | null): boolean {
        if (!(target instanceof Node)) return false
        return regions().some(region => region?.contains(target))
    }

    function onPointerDown(event: PointerEvent) {
        if (isInside(event.target)) return
        onDismiss('outside')
    }

    function onKeyDown(event: KeyboardEvent) {
        if (event.key !== 'Escape') return
        event.stopPropagation()
        onDismiss('escape')
    }

    const openDocument = () => (open.value ? document : null)
    useEventListener(openDocument, 'pointerdown', onPointerDown, {capture: true})
    useEventListener(openDocument, 'keydown', onKeyDown)
}
