/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import type {JobDetails, JobRow} from '~/api/gateway'
import {closedJob, jobSteps, needsHours, needsText} from './jobs'

function details(job: Partial<JobRow>, extra: Partial<JobDetails> = {}): JobDetails {
    return {
        job: {id: 1, kind: 'contract', state: 'open', title: 'Barn', price: 100, fee: 5, blocks: 0, mine: false, ours: false, ...job},
        disputeByOwner: false,
        stock: {},
        canManage: false,
        takeable: false,
        ...extra,
    }
}

describe('jobSteps', () => {
    it('lets the owner describe or cancel a waiting job', () => {
        expect(jobSteps(details({mine: true}))).toEqual(['describe', 'cancel'])
    })

    it('lets the owner approve, reject or dispute work the company says is done', () => {
        expect(jobSteps(details({mine: true, state: 'done'}))).toEqual(['approve', 'reject', 'dispute'])
    })

    it('lets only the side that opened a dispute withdraw it', () => {
        expect(jobSteps(details({mine: true, state: 'disputed'}, {disputeByOwner: true}))).toEqual(['approve', 'withdraw'])
        expect(jobSteps(details({mine: true, state: 'disputed'}))).toEqual(['approve'])
        expect(jobSteps(details({ours: true, state: 'disputed'}, {canManage: true, dispute: 'Owner blocks us'}))).toEqual(['withdraw'])
        expect(jobSteps(details({ours: true, state: 'disputed'}, {canManage: true}))).toEqual([])
        expect(jobSteps(details({ours: true, state: 'disputed'}, {canManage: true, disputeByOwner: true}))).toEqual([])
    })

    it('lets the owner give more time and answer the company\'s request', () => {
        expect(jobSteps(details({mine: true, state: 'taken'}, {hoursLeft: 10}))).toEqual(['approve', 'dispute', 'extend'])
        expect(jobSteps(details({mine: true, state: 'taken'}, {hoursLeft: 0}))).toEqual(['approve', 'dispute'])
        expect(jobSteps(details({mine: true, state: 'taken'}, {hoursLeft: 10, request: {hours: 5, reason: 'Big'}})))
            .toEqual(['approve', 'dispute', 'grant', 'refuse', 'extend'])
        expect(jobSteps(details({mine: true, kind: 'harvest', state: 'taken'}, {hoursLeft: 10}))).toEqual(['end', 'extend'])
        expect(jobSteps(details({mine: true, state: 'taken'}, {hoursLeft: 4, request: {hours: 5, reason: 'Big'}})))
            .toEqual(['approve', 'dispute', 'refuse', 'extend'])
    })

    it('lets the company ask for more time only while it may', () => {
        const taken = {ours: true, state: 'taken' as const}
        expect(jobSteps(details(taken, {canManage: true, hoursLeft: 10, requestsLeft: 1}))).toEqual(['done', 'giveback', 'dispute', 'ask'])
        expect(jobSteps(details(taken, {canManage: true, hoursLeft: 10, requestsLeft: 0}))).toEqual(['done', 'giveback', 'dispute'])
        expect(jobSteps(details(taken, {canManage: true, hoursLeft: 0, requestsLeft: 1}))).toEqual(['done', 'giveback', 'dispute'])
        expect(jobSteps(details(taken, {canManage: true, hoursLeft: 10, requestsLeft: 1, request: {hours: 5, reason: 'Big'}})))
            .toEqual(['done', 'giveback', 'dispute'])
    })

    it('asks for hours before extending and asking, and for a reason before asking and refusing', () => {
        expect(needsHours('extend')).toBe(true)
        expect(needsHours('ask')).toBe(true)
        expect(needsHours('grant')).toBe(false)
        expect(needsText('ask')).toBe(true)
        expect(needsText('refuse')).toBe(true)
        expect(needsText('extend')).toBe(false)
    })

    it('gives a harvest no approval and no disputes', () => {
        expect(jobSteps(details({mine: true, kind: 'harvest', state: 'taken'}))).toEqual(['end'])
        expect(jobSteps(details({ours: true, kind: 'harvest', state: 'taken'}, {canManage: true}))).toEqual(['end', 'giveback'])
    })

    it('leaves area jobs to be finished in game', () => {
        expect(jobSteps(details({ours: true, kind: 'dig', state: 'taken'}, {canManage: true}))).toEqual(['giveback', 'dispute'])
    })

    it('offers members without the permission nothing', () => {
        expect(jobSteps(details({}))).toEqual([])
        expect(jobSteps(details({}, {canManage: true}))).toEqual([])
        expect(jobSteps(details({}, {canManage: true, takeable: true}))).toEqual(['take'])
    })
})

describe('closedJob', () => {
    it('knows the final states', () => {
        expect(closedJob('paid')).toBe(true)
        expect(closedJob('disputed')).toBe(false)
    })
})

describe('needsText', () => {
    it('asks for a reason before rejecting or disputing', () => {
        expect(needsText('reject')).toBe(true)
        expect(needsText('approve')).toBe(false)
    })
})
