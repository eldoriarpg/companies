/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
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
    LEVEL_UP
}
