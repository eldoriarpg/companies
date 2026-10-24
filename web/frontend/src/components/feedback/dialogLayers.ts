/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {onMounted, onUnmounted, readonly, ref, watch, type ComponentPublicInstance, type Ref} from 'vue'
import {browserShallowRef} from '~/util/browserState'

/**
 * Which of two open dialogs is in front.
 *
 * Opening order decides, not the order the components were mounted in: a dialog mounted once for
 * the whole app lands in the document before any dialog a page opens later, and with one fixed
 * layer for every dialog it would come up behind the one it was opened from.
 *
 * The count falls back to the base as soon as nothing is open, so the numbers stay small.
 */
const BASE_LAYER = 50

const layers = browserShallowRef({open: 0, top: BASE_LAYER})

/** The layer a dialog paints on while it is closed. */
export const baseDialogLayer = BASE_LAYER

/** Takes the layer in front of every dialog that is currently open. */
export function claimDialogLayer(): number {
    layers.value.open++
    return ++layers.value.top
}

/** Gives back a layer taken by {@link claimDialogLayer}. */
export function releaseDialogLayer(): void {
    layers.value.open--
    if (layers.value.open <= 0) {
        layers.value.open = 0
        layers.value.top = BASE_LAYER
    }
}

/**
 * The layer a dialog paints on, claimed while it is open and given back when it closes or goes
 * away.

 *
 * @param open whether the dialog is showing
 */
export function useDialogLayer(open: Ref<boolean>): Readonly<Ref<number>> {
    const layer = ref(BASE_LAYER)
    let claimed = false

    onMounted(() => watch(open, (showing) => {
        if (showing === claimed) return
        claimed = showing
        if (showing) layer.value = claimDialogLayer()
        else releaseDialogLayer()
    }, {immediate: true}))

    onUnmounted(() => {
        if (!claimed) return
        claimed = false
        releaseDialogLayer()
    })

    return readonly(layer)
}

/** A press somewhere outside a dialog, as the dialog primitives report it. */
export type PointerDownOutsideEvent = CustomEvent<{ originalEvent: PointerEvent }>

/**
 * The dimmed page behind a dialog: the layer it paints on, the element to hand its `ref`, and the
 * rule for presses outside the dialog.
 *
 * <p>Only a press on the dimmed page itself closes the dialog. Anything else outside it, a panel
 * that some control inside the dialog opened at the end of the page, belongs to the
 * reader's work in the dialog and leaves it open.
 *
 * @param open whether the dialog is showing
 */
export function useDialogOverlay(open: Ref<boolean>) {
    const overlay = ref<ComponentPublicInstance | null>(null)
    const layer = useDialogLayer(open)

    function keepOpenUnlessOverlay(event: PointerDownOutsideEvent) {
        if (event.detail.originalEvent.target !== overlay.value?.$el) event.preventDefault()
    }

    return {overlay, layer, keepOpenUnlessOverlay}
}
