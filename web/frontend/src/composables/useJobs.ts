/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {gatewayApi, type GatewayApi, type JobDetails, type JobList, type JobRow} from '~/api/gateway'

/**
 * The Jobs page: the open jobs, the player's own and their company's, and one of them with its details. A step of a
 * job answers the job as it is afterwards, which replaces the details and the job's row in every list it is in.
 *
 * @param api the gateway's calls, for tests
 * @returns the lists, the details and their loading
 */
export function useJobs(api: GatewayApi = gatewayApi()) {
    const {list, status, load} = useLoaded<JobList>(() => api.read('jobs.list', {}))
    const {detail, detailStatus, open} = useOpened<JobDetails>(id => api.read('jobs.info', {job: id}))

    /**
     * Shows a job as a step answered it: in the details, and in the lists, where a job the player now owns or their
     * company now holds is added and an open job no longer open leaves the open list.
     *
     * @param changed the job afterwards
     */
    function changed(changed: JobDetails): void {
        detail.value = changed
        const current = list.value
        if (!current) return
        const row = changed.job
        const replace = (rows: JobRow[]) => rows.map(entry => (entry.id === row.id ? row : entry))
        const add = (rows: JobRow[]) => (rows.some(entry => entry.id === row.id) ? replace(rows) : [row, ...rows])
        list.value = {
            ...current,
            open: row.state === 'open' ? replace(current.open) : current.open.filter(entry => entry.id !== row.id),
            mine: row.mine ? add(current.mine) : current.mine,
            company: row.ours ? add(current.company) : current.company.filter(entry => entry.id !== row.id),
        }
    }

    /**
     * Reads a job again after the plugin refused a step, as the page may have shown it out of date.
     *
     * @param id the job's id
     */
    async function refresh(id: number): Promise<void> {
        await open(id)
        if (detail.value?.job.id === id) changed(detail.value)
    }

    return {list, status: readonly(status), detail, detailStatus: readonly(detailStatus), load, open, changed, refresh}
}
