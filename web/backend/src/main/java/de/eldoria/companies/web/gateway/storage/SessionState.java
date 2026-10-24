/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.storage;

/**
 * Where a dashboard session stands.
 */
public enum SessionState {
    /**
     * Created for a player, waiting for the code to be entered.
     */
    PENDING,
    /**
     * The right code was entered; the gateway waits for the plugin to confirm the login.
     */
    OPENING,
    /**
     * A browser is logged in with the session's cookie.
     */
    BOUND
}
