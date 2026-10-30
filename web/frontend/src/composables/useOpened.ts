/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */

/**
 * One entry of a list opened beside it, such as an event offer or a job: read when chosen, closed when the choice is
 * cleared. An answer overtaken by another choice is dropped, so a fast click through the list shows the last one.
 *
 * @param read reads the entry with the given id
 * @returns the entry, its loading and the means to open one
 */
export function useOpened<T>(read: (id: number) => Promise<T>) {
    const detail = shallowRef<T | null>(null)
    const detailStatus = ref<'idle' | 'loading' | 'ready' | 'failed'>('idle')
    const latest = useLatest()

    /**
     * Loads one entry.
     *
     * @param id the entry's id, none to close it
     */
    async function open(id: number | null): Promise<void> {
        const ticket = latest.next()
        if (id === null) {
            detail.value = null
            detailStatus.value = 'idle'
            return
        }
        detailStatus.value = 'loading'
        try {
            const found = await read(id)
            if (!latest.is(ticket)) return
            detail.value = found
            detailStatus.value = 'ready'
        } catch {
            if (!latest.is(ticket)) return
            detail.value = null
            detailStatus.value = 'failed'
        }
    }

    return {detail, detailStatus, open}
}

/**
 * A list read once when its page opens.
 *
 * @param read reads the list
 * @returns the list, its loading and the means to load it
 */
export function useLoaded<T>(read: () => Promise<T>) {
    const list = shallowRef<T | null>(null)
    const status = ref<'loading' | 'ready' | 'failed'>('loading')

    /** Loads the list. */
    async function load(): Promise<void> {
        status.value = 'loading'
        try {
            list.value = await read()
            status.value = 'ready'
        } catch {
            status.value = 'failed'
        }
    }

    return {list, status, load}
}
