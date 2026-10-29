/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {EventRow} from '~/api/gateway'
import {hoursLeft} from './format'

/** The states of an offer that closed for good. */
const CLOSED_STATES = ['reached', 'ended', 'depleted', 'cancelled']

/** What the time of an event offer shows: the hours until it starts, until it ends once it runs, or that it closed. */
export interface OfferTime {
    /** `starts` before the start, `ends` after, `closed` once it closed. */
    key: 'starts' | 'ends' | 'closed'
    hours: number
}

/**
 * Whether an offer closed for good: reached, ended, out of money or cancelled.
 *
 * @param state the offer's state
 * @returns true once it closed
 */
export function closedState(state: string): boolean {
    return CLOSED_STATES.includes(state)
}

/**
 * The time an event offer shows: an offer waiting for its start counts down to it, a closed one says so, every other
 * one counts down to its end.
 *
 * @param offer the offer
 * @param now the current time
 * @returns what to count down to and the hours left, rounded up
 */
export function offerTime(offer: Pick<EventRow, 'state' | 'starts' | 'ends'>, now: Date): OfferTime {
    if (closedState(offer.state)) return {key: 'closed', hours: 0}
    if (offer.state === 'scheduled' && new Date(offer.starts).getTime() > now.getTime()) {
        return {key: 'starts', hours: hoursLeft(offer.starts, now)}
    }
    return {key: 'ends', hours: hoursLeft(offer.ends, now)}
}

/**
 * Whether the player may let the company join or leave a contest from the page: a contest that runs, for a member who
 * manages the company's orders. The plugin checks this again.
 *
 * @param offer the offer
 * @param canManage whether the player manages the company's orders
 * @returns true when the join or leave button is shown
 */
export function joinable(offer: Pick<EventRow, 'kind' | 'state'>, canManage: boolean): boolean {
    return canManage && offer.kind === 'contest' && (offer.state === 'active' || offer.state === 'paused')
}

/**
 * The icon material of an offer's item for its mark: the material, or a neutral one for criteria.
 *
 * @param icon the icon the plugin sent, none for criteria
 * @returns a material key
 */
export function offerMaterial(icon: string | null | undefined): string {
    return icon ? `minecraft:${icon}` : 'minecraft:paper'
}
