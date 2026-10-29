/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import type {EventDetails, EventRow, GatewayApi} from '~/api/gateway'
import {fakeApi} from '~/test/fakes'
import {useEvents} from './useEvents'

/**
 * An offer of the Events page.
 *
 * @param id its id
 * @param joined whether the company takes part
 * @returns the offer
 */
function row(id: number, joined = false): EventRow {
    return {
        id,
        kind: 'contest',
        name: `Race ${id}`,
        item: {name: 'Oak Log', icon: 'oak_log'},
        amount: 100,
        delivered: 10,
        percent: 10,
        unitPrice: 1,
        state: 'active',
        starts: '2026-10-29T10:00:00Z',
        ends: '2026-10-30T10:00:00Z',
        prizeTotal: 70,
        joined,
    }
}

/**
 * The details of an offer.
 *
 * @param offer the offer
 * @returns the details
 */
function details(offer: EventRow): EventDetails {
    return {offer, contributors: [], ranking: [], prizes: [], member: true, canManage: true}
}

/** The list and the details of the Events page. */
describe('the events page', () => {
    it('keeps only the details of the offer chosen last', async () => {
        const api = fakeApi()
        let answerFirst: (value: EventDetails) => void = () => {}
        const read = vi.fn()
            .mockImplementationOnce(() => new Promise(resolve => {
                answerFirst = resolve
            }))
            .mockResolvedValueOnce(details(row(2)))
        api.read = read as unknown as GatewayApi['read']
        const events = useEvents(api)

        const first = events.open(1)
        await events.open(2)
        answerFirst(details(row(1)))
        await first

        expect(events.detail.value?.offer.id).toBe(2)
    })

    it('shows a contest as joining answered it in the list too', async () => {
        const api = fakeApi()
        api.read = vi.fn().mockResolvedValue({offers: [row(1), row(2)], member: true, canManage: true}) as unknown as GatewayApi['read']
        const events = useEvents(api)
        await events.load()

        events.changed(details(row(2, true)))

        expect(events.list.value?.offers.map(offer => offer.joined)).toEqual([false, true])
        expect(events.detail.value?.offer.joined).toBe(true)
    })

    it('reads an offer again after a refusal and corrects its row', async () => {
        const api = fakeApi()
        api.read = vi.fn()
            .mockResolvedValueOnce({offers: [row(1)], member: true, canManage: true})
            .mockResolvedValueOnce(details(row(1, true))) as unknown as GatewayApi['read']
        const events = useEvents(api)
        await events.load()

        await events.refresh(1)

        expect(events.detail.value?.offer.joined).toBe(true)
        expect(events.list.value?.offers[0]?.joined).toBe(true)
    })
})
