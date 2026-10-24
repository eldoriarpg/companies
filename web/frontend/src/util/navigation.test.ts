/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {sectionOf} from './navigation'

/**
 * A page belongs to the section whose path it lies under, the deepest one where several do.
 *
 * @vitest-environment node
 */
describe('sectionOf', () => {
    it('finds the section of its own path, with or without a trailing slash', () => {
        expect(sectionOf('/app')?.id).toBe('portfolio')
        expect(sectionOf('/app/orders/')?.id).toBe('orders')
    })

    it('puts a page under a section into that section', () => {
        expect(sectionOf('/app/orders/new')?.id).toBe('orders')
    })

    it('knows nothing outside the dashboard', () => {
        expect(sectionOf('/AB12CD')).toBeUndefined()
    })
})
