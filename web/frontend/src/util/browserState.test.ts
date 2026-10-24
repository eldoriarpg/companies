/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {isReadonly, isShallow} from 'vue'
import {browserRef, browserShallowRef} from './browserState'

describe('browser state in the browser', () => {
    it('is an ordinary ref', () => {
        const toasts = browserRef<string[]>([])

        toasts.value.push('Gespeichert')
        toasts.value = [...toasts.value, 'Gelöscht']

        expect(isReadonly(toasts)).toBe(false)
        expect(toasts.value).toEqual(['Gespeichert', 'Gelöscht'])
    })

    it('keeps a shallow value as it was handed over', () => {
        const blob = new Blob(['Inhalt'])
        const viewed = browserShallowRef<{blob: Blob} | null>(null)

        viewed.value = {blob}

        expect(isShallow(viewed)).toBe(true)
        expect(viewed.value?.blob).toBe(blob)
    })
})
