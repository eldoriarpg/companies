/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.browser;

import de.eldoria.companies.web.api.handshake.VersionStatus;
import org.jspecify.annotations.Nullable;

/**
 * A refused browser request, answered with its status and an {@link Views.ErrorView}.
 */
public final class ApiError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int status;
    private final Code code;
    private final @Nullable Integer attemptsLeft;
    private final transient @Nullable VersionStatus version;

    /**
     * What went wrong. The HTTP status tells cases of one code apart.
     */
    public enum Code {
        /**
         * No session waits for this link, or it expired.
         */
        UNKNOWN_SESSION,
        /**
         * The code is wrong; more attempts are left.
         */
        WRONG_CODE,
        /**
         * The last attempt was used; the session is gone.
         */
        TOO_MANY_ATTEMPTS,
        /**
         * The Turnstile check failed.
         */
        TURNSTILE_FAILED,
        /**
         * Not logged in, or not allowed to do this.
         */
        UNAUTHORIZED,
        /**
         * The server's plugin version is discontinued on the dashboard.
         */
        DISCONTINUED,
        /**
         * No node of the server is connected or answered in time.
         */
        SERVER_OFFLINE,
        /**
         * Too many requests.
         */
        RATE_LIMITED,
        /**
         * Anything else: an unreadable request, a busy session or a failure of the server.
         */
        INTERNAL
    }

    /**
     * @param status the HTTP status
     * @param code the error code
     * @param message a plain sentence
     */
    public ApiError(int status, Code code, String message) {
        this(status, code, message, null, null);
    }

    /**
     * @param status the HTTP status
     * @param code the error code
     * @param message a plain sentence
     * @param attemptsLeft how many codes may still be tried
     * @param version the state of the server's plugin version
     */
    public ApiError(
            int status, Code code, String message, @Nullable Integer attemptsLeft, @Nullable VersionStatus version) {
        super(message);
        this.status = status;
        this.code = code;
        this.attemptsLeft = attemptsLeft;
        this.version = version;
    }

    /**
     * @return the HTTP status
     */
    public int status() {
        return status;
    }

    /**
     * @return the body
     */
    public Views.ErrorView view() {
        return new Views.ErrorView(code, getMessage(), attemptsLeft, version);
    }
}
