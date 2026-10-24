/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
/** @vitest-environment node */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {browserRef, browserShallowRef} from './browserState'

/**
 * A module is evaluated once per server process, so whatever a server render wrote into browser
 * state would reach every request after it. On the server the state therefore keeps its initial
 * value, and a write says so.
 */
describe('browser state on the server', () => {
    afterEach(() => {
        vi.restoreAllMocks()
    })

    it('ignores an assignment and warns about it', () => {
        const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
        const open = browserRef(false)

        open.value = true

        expect(open.value).toBe(false)
        expect(warn).toHaveBeenCalledWith(expect.stringContaining('readonly'), expect.anything())
    })

    it('ignores a change inside the value', () => {
        vi.spyOn(console, 'warn').mockImplementation(() => undefined)
        const toasts = browserRef<string[]>([])
        const counters = browserShallowRef({next: 0})

        toasts.value.push('Gespeichert')
        counters.value.next += 1

        expect(toasts.value).toEqual([])
        expect(counters.value.next).toBe(0)
    })
})
