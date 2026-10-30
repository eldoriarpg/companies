/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * What came of ending or cancelling a server event.
 */
public enum EventChange {
    /**
     * The event changed.
     */
    DONE,
    /**
     * There is no such event.
     */
    UNKNOWN,
    /**
     * The event is closed already, or closed while it was changed.
     */
    CLOSED,
    /**
     * The event can not change that way, such as ending one that has not started; cancel it instead.
     */
    REFUSED,
    /**
     * The change could not be stored.
     */
    FAILED
}
