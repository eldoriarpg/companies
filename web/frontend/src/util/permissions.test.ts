/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import {editable, GRANTABLE, holds, mayKick} from './permissions'

/** What a member may do to another, as the plugin decides it. */
describe('company permissions', () => {
    it('lets the owner hold every permission', () => {
        expect(holds(['OWNER'], 'TREASURY')).toBe(true)
        expect(holds(['KICK'], 'TREASURY')).toBe(false)
        expect(holds(['TREASURY'], 'TREASURY')).toBe(true)
    })

    it('lets nobody remove the owner and kickers remove only members who may not kick', () => {
        expect(mayKick(['OWNER'], ['KICK'])).toBe(true)
        expect(mayKick(['OWNER'], ['OWNER'])).toBe(false)
        expect(mayKick(['KICK'], [])).toBe(true)
        expect(mayKick(['KICK'], ['KICK', 'INVITE'])).toBe(false)
        expect(mayKick(['INVITE'], [])).toBe(false)
    })

    it('lets a member change only the permissions they hold', () => {
        expect(editable(['OWNER'])).toEqual(GRANTABLE)
        expect(editable(['MANAGE_PERMISSIONS', 'KICK'])).toEqual(['KICK', 'MANAGE_PERMISSIONS'])
        expect(editable(['KICK'])).toEqual([])
    })
})
