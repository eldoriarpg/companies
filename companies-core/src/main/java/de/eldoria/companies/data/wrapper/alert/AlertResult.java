/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.alert;

/**
 * The outcome of saving an alert.
 */
public enum AlertResult {
    /**
     * The alert was saved.
     */
    CREATED,
    /**
     * The company already has an alert with this name.
     */
    NAME_TAKEN,
    /**
     * The company has as many alerts as its limit allows.
     */
    LIMIT_REACHED,
    /**
     * The company does not exist or the database failed.
     */
    FAILED
}
