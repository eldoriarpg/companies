/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {ALEX, claimed, member, NODE, patch, snapshot} from '~/test/snapshots'
import {applyPatch, isNewer, Unapplicable} from './patch'

/** How the patches of the plugin change the snapshot, the same way the gateway's patch applier does. */
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

    it('removes a list entry by its key and ignores an unknown key', () => {
        expect(applyPatch(snapshot(), patch(42, {path: 'company.orders', op: 'REMOVE', key: '101'})).company?.orders).toEqual([])
        expect(applyPatch(snapshot(), patch(42, {path: 'company.orders', op: 'REMOVE', key: '999'})).company?.orders).toHaveLength(1)
    })

    it('matches list entries by id, uuid or key only', () => {
        const before = {...snapshot(), rankings: {...snapshot().rankings, entries: [{place: 1, company: 7, name: 'Diamond Diggers', value: 1}]}}
        const patched = applyPatch(before, patch(42, {path: 'rankings.entries', op: 'UPSERT', key: '7', value: {place: 2, company: 7, name: 'Diamond Diggers', value: 2}}))
        expect(patched.rankings.entries).toHaveLength(2)
    })

    it('sets or deletes the value at the path without a key', () => {
        const set = applyPatch(snapshot(), patch(42, {path: 'company.description', op: 'UPSERT', value: 'We dig deeper.'}))
        expect(set.company?.description).toBe('We dig deeper.')
        const removed = applyPatch(set, patch(43, {path: 'company.description', op: 'REMOVE'}))
        expect(removed.company).not.toHaveProperty('description')
    })

    it('sets and deletes a property of an object named by the key', () => {
        const set = applyPatch(snapshot(), patch(42, {path: 'company', op: 'UPSERT', key: 'tag', value: 'DIG'}))
        expect(set.company?.tag).toBe('DIG')
        expect(applyPatch(set, patch(43, {path: 'company', op: 'REMOVE', key: 'tag'})).company).not.toHaveProperty('tag')
    })

    it('refuses a patch whose parent is missing instead of creating it', () => {
        const before = {...snapshot(), company: undefined}
        expect(() => applyPatch(before, patch(42, {path: 'company.treasury', op: 'REPLACE', value: {balance: 10}}))).toThrow(Unapplicable)
        expect(() => applyPatch(before, patch(42, {path: 'company.members', op: 'UPSERT', key: ALEX, value: {}}))).toThrow(Unapplicable)
    })

    it('refuses a keyed patch on a value that is no list or object', () => {
        expect(() => applyPatch(snapshot(), patch(42, {path: 'player.unreadInbox', op: 'UPSERT', key: 'x', value: 1}))).toThrow(Unapplicable)
    })

    it('does not change the snapshot it was given', () => {
        const before = snapshot()
        applyPatch(before, patch(42, {path: 'player.unreadInbox', op: 'REPLACE', value: 9}))
        expect(before.player.unreadInbox).toBe(3)
    })
})

/** Which patches are already contained in the snapshot. */
describe('the order of patches', () => {
    it('applies anything above the last of its node, gaps included, and the first of a new node', () => {
        expect(isNewer({[NODE]: 41}, {node: NODE, seq: 42})).toBe(true)
        expect(isNewer({[NODE]: 41}, {node: NODE, seq: 47})).toBe(true)
        expect(isNewer({[NODE]: 41}, {node: 'other', seq: 7})).toBe(true)
    })

    it('drops a patch at or below the last of its node', () => {
        expect(isNewer({[NODE]: 41}, {node: NODE, seq: 41})).toBe(false)
        expect(isNewer({[NODE]: 41}, {node: NODE, seq: 12})).toBe(false)
    })
})
