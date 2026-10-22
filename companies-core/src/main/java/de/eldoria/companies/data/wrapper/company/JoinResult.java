/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.wrapper.company;

/**
 * Outcome of adding a player to a company.
 */
public enum JoinResult {
    /**
     * The player is now a member; their open invites and applications were removed.
     */
    JOINED,
    /**
     * The player is already a member of a company, this one or another.
     */
    ALREADY_MEMBER,
    /**
     * The company has reached its member limit.
     */
    COMPANY_FULL,
    /**
     * The company does not exist anymore.
     */
    UNKNOWN_COMPANY,
    /**
     * The database refused the change for another reason; the error was logged and nothing was changed.
     */
    FAILED
}
