/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.perk.OwnedPerk;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The perks companies own. A company owns a perk at most once; the perks go with the company when it is deleted. The
 * methods block and are called off the main thread.
 */
public abstract class APerkData {
    /**
     * @param company id of the company
     * @return the perks it owns, oldest first
     */
    public abstract List<OwnedPerk> perks(int company);

    /**
     * Stores a perk of a company.
     *
     * @param company id of the company
     * @param perk    id of the perk
     * @param bought  when the company got it
     * @return true when stored, false when the company owns it already
     */
    public abstract boolean add(int company, String perk, LocalDateTime bought);

    /**
     * Removes a perk of a company.
     *
     * @param company id of the company
     * @param perk    id of the perk
     * @return true when the company owned it
     */
    public abstract boolean remove(int company, String perk);
}
