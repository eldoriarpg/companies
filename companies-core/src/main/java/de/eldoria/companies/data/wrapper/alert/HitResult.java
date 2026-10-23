/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.alert;

/**
 * What to do with a new order that matches an alert.
 */
public enum HitResult {
    /**
     * Notify the company now; the rate limit window starts again.
     */
    NOTIFY,
    /**
     * The alert notified recently; the match was counted for the digest.
     */
    SUPPRESSED,
    /**
     * The alert was removed or disabled meanwhile; nothing was counted.
     */
    GONE
}
