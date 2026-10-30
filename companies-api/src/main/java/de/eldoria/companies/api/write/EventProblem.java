/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * Why a server event was not created.
 */
public enum EventProblem {
    /**
     * The name is blank or longer than 64 characters.
     */
    NAME,
    /**
     * The value does not fit the kind: a reduction must be above 0 and at most 100, a factor above 1.
     */
    VALUE,
    /**
     * The end is not after the start, has passed, or lies more than a year ahead.
     */
    TIMES,
    /**
     * A fee reduction was limited to levels, or a level is below 1.
     */
    LEVEL_SCOPE,
    /**
     * A material names no item.
     */
    MATERIALS,
    /**
     * A multiplier names neither minted money nor an account.
     */
    FUNDING,
    /**
     * A multiplier paid from an account names none.
     */
    ACCOUNT,
    /**
     * Minted money has no budget, or a budget is not above 0.
     */
    BUDGET,
    /**
     * The event could not be stored.
     */
    STORAGE
}
