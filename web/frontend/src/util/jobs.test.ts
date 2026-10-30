/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it} from 'vitest'
import type {JobDetails, JobRow} from '~/api/gateway'
import {closedJob, jobSteps, needsText} from './jobs'

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
