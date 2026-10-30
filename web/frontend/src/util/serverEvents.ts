/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import type {AdminEventDraft, ServerEventKind} from '~/api/gateway'

/** The admin's form of a new server event, as typed. */
export interface ServerEventForm {
    name: string
    kind: ServerEventKind
    value: string
    materials: string
    levels: string
    start: string
    end: string
    funding: 'ACCOUNT' | 'MINTED'
    account: string
    budget: string
}

/**
 * The arguments of `admin.events.create` for a form: the fields as typed, the money only for a reward multiplier and an
 * empty field as missing, so the plugin reads them as the command's form does.
 *
 * @param form the form
 * @returns the draft
 */
export function eventDraftOf(form: ServerEventForm): AdminEventDraft {
    const draft: AdminEventDraft = {
        name: form.name.trim(),
        kind: form.kind.toUpperCase(),
        value: form.value.trim(),
        start: form.start.trim(),
        end: form.end.trim(),
    }
    const materials = form.materials.trim()
    const levels = form.levels.trim()
    if (materials !== '') draft.materials = materials
    if (levels !== '' && form.kind !== 'fee_reduction') draft.levels = levels
    if (form.kind === 'reward_multiplier') {
        draft.funding = form.funding
        if (form.funding === 'ACCOUNT' && form.account.trim() !== '') draft.account = form.account.trim()
        if (form.budget.trim() !== '') draft.budget = form.budget.trim()
    }
    return draft
}

/** The changes a server event's state and kind allow. */
export interface ServerEventActions {
    end: boolean
    cancel: boolean
    extend: boolean
    budget: boolean
}

/**
 * The changes of a server event: ending only while it runs, cancelling and extending while it waits or runs, and the
 * budget of a running or waiting reward multiplier.
 *
 * @param state the event's state
 * @param kind  what it changes
 * @returns the changes allowed
 */
export function serverEventActions(state: string, kind: ServerEventKind): ServerEventActions {
    const live = state === 'scheduled' || state === 'active'
    return {
        end: state === 'active',
        cancel: live,
        extend: live,
        budget: live && kind === 'reward_multiplier',
    }
}

/**
 * Names who closed an event in words: the primary's markers have keys of their own.
 *
 * @param closedBy who closed it, as stored
 * @returns `missed`, `template`, `time` when nobody did, or `admin`
 */
export function closerKind(closedBy: string | null | undefined): 'missed' | 'template' | 'time' | 'admin' {
    if (closedBy == null) return 'time'
    if (closedBy === '#missed') return 'missed'
    if (closedBy === '#template') return 'template'
    return 'admin'
}
