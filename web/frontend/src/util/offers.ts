/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {AdminOfferDraft, OfferKind} from '~/api/gateway'

/** The admin's form of a new goal or contest, as typed. */
export interface OfferForm {
    kind: OfferKind
    name: string
    money: string
    itemKind: 'material' | 'criteria'
    /** The material or the criteria. */
    item: string
    amount: number | undefined
    price: number | undefined
    chunk: number | undefined
    open: number | undefined
    /** The money of the first places, separated by `+`, `,` or spaces. */
    prizes: string
    start: number | undefined
    hours: number | undefined
    budget: number | undefined
}

/**
 * The prizes of a contest as typed: numbers separated by `+`, commas or spaces; anything else is sent as typed for the
 * plugin to refuse.
 *
 * @param text the prizes as typed
 * @returns the money of the first places, first place first
 */
export function prizesOf(text: string): number[] {
    return text.split(/[+,\s]+/)
        .map(part => part.trim())
        .filter(part => part !== '')
        .map(Number)
}

/**
 * The arguments of `admin.offers.create` for a form: only what the kind of offer takes, an empty field as missing.
 *
 * @param form the form
 * @returns the draft
 */
export function draftOf(form: OfferForm): AdminOfferDraft {
    const draft: AdminOfferDraft = {
        kind: form.kind,
        name: form.name.trim(),
        money: form.money.trim(),
        amount: form.amount ?? 0,
        price: form.price ?? 0,
        start: form.start ?? 0,
        hours: form.hours ?? 0,
    }
    if (form.itemKind === 'criteria') draft.criteria = form.item.trim()
    else draft.material = form.item.trim()
    if (form.kind === 'goal') {
        draft.chunk = form.chunk ?? 0
        draft.open = form.open ?? 0
    } else {
        draft.prizes = prizesOf(form.prizes)
    }
    if (form.budget !== undefined) draft.budget = form.budget
    return draft
}

/**
 * Which changes an offer takes in its state, as the plugin allows them.
 *
 * @param state the offer's state
 * @param kind the offer's kind
 * @returns whether it can be paused, resumed, cancelled and have its waiting chunks changed
 */
export function offerActions(state: string, kind: OfferKind): {pause: boolean, resume: boolean, cancel: boolean, open: boolean} {
    const running = state === 'scheduled' || state === 'active' || state === 'paused'
    return {
        pause: state === 'scheduled' || state === 'active',
        resume: state === 'paused',
        cancel: running,
        open: running && kind === 'goal',
    }
}
