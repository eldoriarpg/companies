/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {baseDialogLayer, claimDialogLayer, releaseDialogLayer} from './dialogLayers'

describe('dialogLayers', () => {
    it('puts each dialog in front of the ones already open', () => {
        const first = claimDialogLayer()
        const second = claimDialogLayer()

        expect(first).toBeGreaterThan(baseDialogLayer)
        expect(second).toBeGreaterThan(first)

        releaseDialogLayer()
        releaseDialogLayer()
    })

    /**
     * A session opens dialogs all day. Without the fall back the layers would climb past the toasts,
     * which have to stay readable over any dialog.
     */
    it('starts over once nothing is open any more', () => {
        const first = claimDialogLayer()
        claimDialogLayer()
        releaseDialogLayer()
        releaseDialogLayer()

        expect(claimDialogLayer()).toBe(first)

        releaseDialogLayer()
    })
})
