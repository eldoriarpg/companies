/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.join;

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
     * The company has reached the member limit of its level.
     */
    COMPANY_FULL,
    /**
     * No company has the given id.
     */
    UNKNOWN_COMPANY,
    /**
     * The database refused the change for another reason; the error was logged and nothing was changed.
     */
    FAILED
}
