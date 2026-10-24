/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

/**
 * A write operation was requested before Companies could perform it.
 * <p>
 * Write operations need the economy, which Companies sets up once the server has started. Futures of write operations
 * complete exceptionally with this exception until then, and for as long as Companies runs without an economy.
 */
public class ApiNotReadyException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception.
     *
     * @param message what was not ready
     */
    public ApiNotReadyException(String message) {
        super(message);
    }
}
