/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api.write;

/**
 * Why a company was not founded.
 */
public enum FoundProblem {
    /**
     * The name is blank or too long.
     */
    INVALID_NAME,
    /**
     * Another company has the name.
     */
    NAME_TAKEN,
    /**
     * The owner is a member of a company already.
     */
    ALREADY_MEMBER,
    /**
     * The owner cannot pay the founding price.
     */
    NO_MONEY,
    /**
     * A listener of {@link de.eldoria.companies.events.company.PreCompanyCreateEvent} cancelled it.
     */
    CANCELLED,
    /**
     * The company could not be saved.
     */
    FAILED
}
