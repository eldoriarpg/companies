/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * Why a home was not set.
 */
public enum HomeProblem {
    /**
     * No company has the id.
     */
    NO_COMPANY,
    /**
     * The company has areas and the location lies outside all of them while {@code homeInsideArea} is on.
     */
    NOT_IN_AREA,
    /**
     * The home could not be saved.
     */
    FAILED
}
