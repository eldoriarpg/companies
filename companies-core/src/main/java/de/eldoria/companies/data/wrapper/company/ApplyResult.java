/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

/**
 * Outcome of storing an application.
 */
public enum ApplyResult {
    /**
     * The application was stored.
     */
    CREATED,
    /**
     * The player already has an open application to this company.
     */
    ALREADY_PENDING,
    /**
     * The player already has the maximum number of open applications.
     */
    TOO_MANY,
    /**
     * The database refused the change for another reason; the error was logged and nothing was changed.
     */
    FAILED
}
