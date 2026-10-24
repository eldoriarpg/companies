/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.session;

import de.eldoria.companies.web.gateway.storage.StoredSession;

/**
 * The outcome of entering a login code.
 */
public sealed interface LoginResult {
    /**
     * The browser is logged in.
     *
     * @param cookie the cookie value to set
     * @param session the session
     */
    record LoggedIn(String cookie, StoredSession session) implements LoginResult {}

    /**
     * The code is wrong.
     *
     * @param attemptsLeft how many more codes may be tried; at 0 the session is gone
     */
    record WrongCode(int attemptsLeft) implements LoginResult {}

    /**
     * No pending session has this short id, or its code expired.
     */
    record Unknown() implements LoginResult {}

    /**
     * Another browser is logging in with this code right now.
     */
    record Busy() implements LoginResult {}

    /**
     * No node of the server is connected, or none answered in time.
     */
    record Offline() implements LoginResult {}

    /**
     * The server's plugin version is discontinued on the dashboard.
     *
     * @param message the policy's explanation
     */
    record Discontinued(String message) implements LoginResult {}

    /**
     * The plugin refused the login, e.g. for a session it did not create.
     *
     * @param message why
     */
    record Refused(String message) implements LoginResult {}
}
