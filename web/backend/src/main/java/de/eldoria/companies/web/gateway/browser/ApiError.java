/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.web.gateway.browser;

import org.jspecify.annotations.Nullable;

/**
 * A refused browser request, answered with its status and an {@link Views.ErrorView}.
 */
public final class ApiError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int status;
    private final String error;
    private final @Nullable Integer attemptsLeft;

    /**
     * @param status the HTTP status
     * @param error the stable error code
     * @param message a plain sentence
     */
    public ApiError(int status, String error, String message) {
        this(status, error, message, null);
    }

    /**
     * @param status the HTTP status
     * @param error the stable error code
     * @param message a plain sentence
     * @param attemptsLeft how many codes may still be tried
     */
    public ApiError(int status, String error, String message, @Nullable Integer attemptsLeft) {
        super(message);
        this.status = status;
        this.error = error;
        this.attemptsLeft = attemptsLeft;
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
        String message = getMessage();
        return new Views.ErrorView(error, message == null ? error : message, attemptsLeft);
    }
}
