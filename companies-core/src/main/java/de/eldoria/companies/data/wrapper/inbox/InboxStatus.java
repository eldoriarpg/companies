/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.inbox;

/**
 * How a notification is stored when it is sent.
 */
public enum InboxStatus {
    /**
     * Not shown yet. The node of the recipient shows it live, or it is counted in their next join summary.
     */
    PENDING,
    /**
     * Never shown live and not counted in a join summary, but unread in the inbox. Used for muted categories.
     */
    DELIVERED,
    /**
     * Shown live in full, which counts as read.
     */
    READ
}
