/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */

/**
 * Numbers the requests of one list, so the answer of a request a newer one overtook is told apart and dropped. A
 * search typed fast sends several requests, and their answers may come back in any order.
 *
 * @returns the means to number a request and to ask whether it is still the latest
 */
export function useLatest() {
    let counter = 0
    return {
        /**
         * Numbers a new request.
         *
         * @returns its ticket
         */
        next(): number {
            return ++counter
        },

        /**
         * Whether a request is still the latest.
         *
         * @param ticket its ticket
         * @returns true when no newer one was made since
         */
        is(ticket: number): boolean {
            return ticket === counter
        },
    }
}
