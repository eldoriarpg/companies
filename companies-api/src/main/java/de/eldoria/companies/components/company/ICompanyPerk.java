/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.company;

import java.time.LocalDateTime;

/**
 * A perk a company owns: bought from its treasury or granted by an admin. A perk is usable while the server offers it
 * and the company has reached its minimum level; below that level it stays owned but paused.
 */
public interface ICompanyPerk {
    /**
     * The company owning the perk.
     *
     * @return the id of the company
     */
    int company();

    /**
     * The perk.
     *
     * @return the id of the perk, lower case
     */
    String perk();

    /**
     * When the company got the perk, in UTC.
     *
     * @return the time
     */
    LocalDateTime bought();

    /**
     * Whether the company may use the perk now: the server offers it and the company has reached its minimum level.
     *
     * @return true when usable, false when paused
     */
    boolean usable();
}
