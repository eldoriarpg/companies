/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */

/**
 * An order id as the address carries it.
 *
 * @param raw the parameter's text
 * @returns the id, or nothing for text that is none
 */
export function orderIdOf(raw: string): number | null {
    const value = Number(raw)
    return Number.isInteger(value) && value > 0 ? value : null
}

/**
 * The entry of a list whose details are open, kept in the address as a query parameter. Opening one adds a history
 * entry, so the browser's back button and a phone's back gesture close the details again.
 *
 * @param parameter the query parameter, such as `order`
 * @param parse reads an entry's id from the parameter's text, nothing where it is none
 * @returns the open entry's id and the means to open and close it
 */
export function useSelection<T extends string | number>(parameter: string, parse: (raw: string) => T | null) {
    const route = useRoute()
    const router = useRouter()

    const id = computed<T | null>(() => {
        const raw = route.query[parameter]
        return typeof raw === 'string' && raw !== '' ? parse(raw) : null
    })

    return {
        id,

        /**
         * Opens the details of an entry.
         *
         * @param next the entry's id
         */
        open(next: T): void {
            if (next !== id.value) void router.push({query: {...route.query, [parameter]: String(next)}})
        },

        /** Closes the details, going back where opening them came from. */
        close(): void {
            if (id.value === null) return
            if (window.history.state?.back) router.back()
            else void router.replace({query: {...route.query, [parameter]: undefined}})
        },
    }
}
