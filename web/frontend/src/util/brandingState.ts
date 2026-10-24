/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import type {ServerBranding} from '~/api/gateway'

/**
 * The branding of the server the page belongs to, from the waiting session on the login page and from the server
 * info on the dashboard. It names the browser tab and gives it its icon.
 *
 * @returns the branding, none before it is known
 */
export function brandingState() {
    return useState<ServerBranding | null>('brandingState', () => null)
}
