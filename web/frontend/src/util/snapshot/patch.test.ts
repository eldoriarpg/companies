/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {ALEX, claimed, member, NODE, patch, snapshot} from '~/test/snapshots'
import {applyPatch, arrivalOf} from './patch'

/** How the patches of the plugin change the snapshot the dashboard shows. */
describe('applying a patch', () => {
    it('replaces a value', () => {
        const patched = applyPatch(snapshot(), patch(42, {path: 'player.unreadInbox', op: 'REPLACE', value: 4}))
        expect(patched.player.unreadInbox).toBe(4)
    })

    it('updates a list entry by its key and keeps its place', () => {
        const patched = applyPatch(snapshot(), patch(42, {path: 'company.members', op: 'UPSERT', key: ALEX, value: member(ALEX, 'Alex', 2274)}))
        expect(patched.company?.members.map(entry => entry.stats.items)).toEqual([840, 2274])
    })

    it('appends an entry it does not know', () => {
        const patched = applyPatch(snapshot(), patch(42, {path: 'company.orders', op: 'UPSERT', key: '102', value: claimed(102, 0)}))
        expect(patched.company?.orders.map(order => order.id)).toEqual([101, 102])
    })

    it('removes a list entry by its key', () => {
        const patched = applyPatch(snapshot(), patch(42, {path: 'company.orders', op: 'REMOVE', key: '101'}))
        expect(patched.company?.orders).toEqual([])
    })

    it('replaces a whole object and creates its parents', () => {
        const before = {...snapshot(), company: undefined}
        const patched = applyPatch(before, patch(42, {path: 'company.treasury', op: 'REPLACE', value: {balance: 10}}))
        expect(patched.company?.treasury?.balance).toBe(10)
    })

    it('leaves a path alone whose parent is gone', () => {
        const before = {...snapshot(), company: undefined}
        const patched = applyPatch(before, patch(42, {path: 'company.members', op: 'UPSERT', key: ALEX, value: member(ALEX, 'Alex', 1)}))
        expect(patched.company).toBeUndefined()
    })

    it('sets and deletes a field named by the key', () => {
        const set = applyPatch(snapshot(), patch(42, {path: 'company', op: 'UPSERT', key: 'description', value: 'We dig deeper.'}))
        expect(set.company?.description).toBe('We dig deeper.')
        const removed = applyPatch(set, patch(43, {path: 'company', op: 'REMOVE', key: 'description'}))
        expect(removed.company?.description).toBeUndefined()
    })

    it('does not change the snapshot it was given', () => {
        const before = snapshot()
        applyPatch(before, patch(42, {path: 'player.unreadInbox', op: 'REPLACE', value: 9}))
        expect(before.player.unreadInbox).toBe(3)
    })
})

/** When a patch is applied, dropped or makes the dashboard load the snapshot again. */
describe('the order of patches', () => {
    it('applies the next patch of a node and the first of a new node', () => {
        expect(arrivalOf({[NODE]: 41}, {node: NODE, seq: 42})).toBe('apply')
        expect(arrivalOf({[NODE]: 41}, {node: 'other', seq: 7})).toBe('apply')
    })

    it('drops a patch the snapshot already holds', () => {
        expect(arrivalOf({[NODE]: 41}, {node: NODE, seq: 41})).toBe('old')
    })

    it('reports a gap', () => {
        expect(arrivalOf({[NODE]: 41}, {node: NODE, seq: 43})).toBe('gap')
    })
})
