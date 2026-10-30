/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.integrations.discord;

/**
 * The events that can be posted to the Discord webhook.
 */
public enum WebhookEvent {
    /**
     * An order was created.
     */
    ORDER_CREATED,
    /**
     * An order was fully delivered.
     */
    ORDER_COMPLETED,
    /**
     * A company reached a higher level.
     */
    LEVEL_UP,
    /**
     * An event offer, a goal or a contest, started.
     */
    OFFER_STARTED,
    /**
     * The progress of a goal passed 25, 50, 75 or 100 %.
     */
    GOAL_PROGRESS,
    /**
     * A contest closed with its ranking, or was cancelled.
     */
    CONTEST_RESULT,
    /**
     * A server event started.
     */
    EVENT_STARTED,
    /**
     * A server event ended: at its end, early by an admin, cancelled after it started, or when its money ran out.
     */
    EVENT_ENDED
}
