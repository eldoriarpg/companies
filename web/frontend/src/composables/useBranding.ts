/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import type {ServerBranding} from '~/api/gateway'
import {brandingState} from '~/util/brandingState'

/**
 * The branding of the server the page belongs to: its name and icon on the login page, in the header, in the browser
 * tab's title and as its icon.
 *
 * @returns the branding and its setter
 */
export function useBranding() {
    const branding = brandingState()
    return {
        branding: readonly(branding),

        /**
         * Shows another server's branding, or none.
         *
         * @param next the branding
         */
        show(next: ServerBranding | null): void {
            branding.value = next
        },
    }
}
