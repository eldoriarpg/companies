/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {JobDetails, JobState} from '~/api/gateway'

/** A step of a job's life the Jobs page offers as a button. */
export type JobStep =
    | 'take'
    | 'giveback'
    | 'done'
    | 'end'
    | 'approve'
    | 'reject'
    | 'dispute'
    | 'withdraw'
    | 'cancel'
    | 'describe'
    | 'extend'
    | 'grant'
    | 'refuse'
    | 'ask'
    | 'askCancel'
    | 'cancelAccept'
    | 'cancelRefuse'
    | 'collect'
    | 'post'
    | 'edit'
    | 'remove'
    | 'supply'
    | 'inventory'

/** The states in which a job is over for good. */
const CLOSED: readonly JobState[] = ['paid', 'settled', 'cancelled', 'expired']

/**
 * @param state the state of a job
 * @returns whether the job is over for good
 */
export function closedJob(state: JobState): boolean {
    return CLOSED.includes(state)
}

/**
 * @param details a job with what its page shows
 * @returns whether it is a supplied draft whose stock still lacks materials
 */
export function lacking(details: JobDetails): boolean {
    return Boolean(details.supplied) && Object.keys(details.missing ?? {}).length > 0
}

/**
 * The steps a player may take on a job, as the plugin allows them: the owner describes or cancels a waiting job,
 * approves, rejects or disputes the work and ends a harvest; a member who manages the company's orders takes a job,
 * gives it back, says a contract is done, ends a harvest and disputes. A dispute is withdrawn only by the side that
 * opened it. Area jobs other than harvests are finished in game, where their area is checked. Harvest jobs have no
 * approval and no disputes. While a company works on a job, the owner may give it more time and answers the company's
 * request for it; the company asks while it has no open request, requests left and hours the job may still get. The
 * owner may ask the company working on a job to let it be cancelled, once until it answers; the company agrees or
 * refuses. The owner of a harvest job collects its stock into their item returns.
 *
 * @param details the job
 * @param online whether the player is online on a server, where their inventory can be read
 * @returns the steps, in the order the page shows them
 */
export function jobSteps(details: JobDetails, online = false): JobStep[] {
    const {job} = details
    const steps: JobStep[] = []
    const harvest = job.kind === 'harvest'
    if (job.mine) {
        if (job.state === 'draft') return lacking(details) ? [...(online ? ['inventory' as const] : []), 'supply', 'post', 'edit', 'remove'] : ['post', 'edit', 'remove']
        if (job.state === 'open') steps.push('describe', 'cancel')
        if (job.state === 'done') steps.push('approve', 'reject')
        if (job.state === 'taken' && !harvest) steps.push('approve')
        if (job.state === 'taken' && harvest) steps.push('end')
        if ((job.state === 'taken' || job.state === 'done') && !harvest) steps.push('dispute')
        if (job.state === 'taken' && details.request) {
            if (details.request.hours <= (details.hoursLeft ?? 0)) steps.push('grant')
            steps.push('refuse')
        }
        if (job.state === 'taken' && (details.hoursLeft ?? 0) > 0) steps.push('extend')
        if (job.state === 'taken' && !harvest && !details.cancelAsked) steps.push('askCancel')
        if (harvest && Object.keys(details.stock ?? {}).length > 0) steps.push('collect')
        if (job.state === 'disputed') {
            steps.push('approve')
            if (details.disputeByOwner) steps.push('withdraw')
        }
        return steps
    }
    if (!details.canManage) return steps
    if (details.takeable) steps.push('take')
    if (job.ours) {
        if (job.state === 'taken' && job.kind === 'contract') steps.push('done')
        if (job.state === 'taken' && harvest) steps.push('end')
        if (job.state === 'taken' || job.state === 'done') steps.push('giveback')
        if ((job.state === 'taken' || job.state === 'done') && !harvest) steps.push('dispute')
        if (job.state === 'disputed' && details.dispute != null && !details.disputeByOwner) steps.push('withdraw')
        if (job.state === 'taken' && !details.request && (details.requestsLeft ?? 0) > 0 && (details.hoursLeft ?? 0) > 0) {
            steps.push('ask')
        }
        if (job.state === 'taken' && details.cancelAsked) steps.push('cancelAccept', 'cancelRefuse')
    }
    return steps
}

/**
 * @param step a step
 * @returns whether the step needs a written reason or text first
 */
export function needsText(step: JobStep): boolean {
    return step === 'reject' || step === 'dispute' || step === 'describe' || step === 'ask' || step === 'refuse'
}

/**
 * @param step a step
 * @returns whether the step needs a number of hours first
 */
export function needsHours(step: JobStep): boolean {
    return step === 'extend' || step === 'ask'
}

/**
 * @param step a step
 * @returns whether the step undoes or gives up something, so its button warns
 */
export function warns(step: JobStep): boolean {
    return step === 'cancel' || step === 'giveback' || step === 'dispute' || step === 'reject' || step === 'end' || step === 'refuse'
        || step === 'askCancel' || step === 'cancelAccept' || step === 'remove'
}
