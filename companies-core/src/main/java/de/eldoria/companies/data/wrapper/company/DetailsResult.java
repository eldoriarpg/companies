/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

/**
 * Outcome of saving the details of a company.
 */
public enum DetailsResult {
    /**
     * The details were saved.
     */
    SAVED,
    /**
     * Another company already uses the tag; nothing was saved.
     */
    TAG_TAKEN,
    /**
     * The company does not exist anymore.
     */
    UNKNOWN_COMPANY,
    /**
     * The database refused the change for another reason; the error was logged.
     */
    FAILED
}
