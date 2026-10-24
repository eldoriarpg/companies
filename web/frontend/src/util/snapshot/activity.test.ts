/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {Patch} from '~/api/generated/protocol'
import {ALEX, claimed, member, patch, snapshot} from '~/test/snapshots'
import {activityOf, addActivity, type ActivityEntry} from './activity'
import {applyPatch} from './patch'

const TIME = '2026-10-24T12:41:00Z'

/**
 * The lines one patch adds, read from the test company before and after it.
 *
 * @param change the patch
 * @returns the lines
 */
function linesOf(change: Patch) {
    const before = snapshot()
    return activityOf(before.company, applyPatch(before, change).company, change, TIME)
}

/** The live feed of the company, read from the patches. */
describe('company activity', () => {
    it('names the member who delivered', () => {
        expect(linesOf(patch(42, {path: 'company.members', op: 'UPSERT', key: ALEX, value: member(ALEX, 'Alex', 2274)})))
            .toEqual([{kind: 'delivered', time: TIME, actor: 'Alex', amount: 64}])
    })

    it('names the order delivered to', () => {
        expect(linesOf(patch(42, {path: 'company.orders', op: 'UPSERT', key: '101', value: claimed(101, 40)})))
            .toEqual([{kind: 'delivered', time: TIME, order: 'Diamonds for a beacon', item: 'Diamond', amount: 20}])
    })

    it('tells accepted, done and dropped orders apart', () => {
        expect(linesOf(patch(42, {path: 'company.orders', op: 'UPSERT', key: '102', value: claimed(102, 0)}))[0]?.kind).toBe('accepted')
        expect(linesOf(patch(42, {path: 'company.orders', op: 'REMOVE', key: '101'}))[0]?.kind).toBe('dropped')
    })

    it('reports a new level, a new member and money moved', () => {
        expect(linesOf(patch(42, {path: 'company.level', op: 'REPLACE', value: {level: 4, name: 'Corporation'}})))
            .toEqual([{kind: 'level', time: TIME, level: 'Corporation'}])
        expect(linesOf(patch(42, {path: 'company.members', op: 'UPSERT', key: 'new', value: member('new', 'Wren', 0)}))[0])
            .toEqual({kind: 'joined', time: TIME, actor: 'Wren'})
        expect(linesOf(patch(42, {path: 'company.treasury', op: 'REPLACE', value: {balance: 2000}}))[0])
            .toEqual({kind: 'withdrawal', time: TIME, amount: 500})
    })

    it('joins the two halves of one delivery into one line', () => {
        const byMember: ActivityEntry = {id: 'a', kind: 'delivered', time: TIME, actor: 'Alex', amount: 64}
        const byOrder: ActivityEntry = {id: 'b', kind: 'delivered', time: TIME, order: 'Beacon', item: 'Diamond', amount: 64}
        expect(addActivity([byMember], byOrder)).toEqual([{...byMember, order: 'Beacon', item: 'Diamond'}])
    })

    it('keeps deliveries of other amounts apart', () => {
        const byMember: ActivityEntry = {id: 'a', kind: 'delivered', time: TIME, actor: 'Alex', amount: 64}
        const byOrder: ActivityEntry = {id: 'b', kind: 'delivered', time: TIME, order: 'Beacon', amount: 32}
        expect(addActivity([byMember], byOrder)).toEqual([byOrder, byMember])
    })
})
