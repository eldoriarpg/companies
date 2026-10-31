/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.area.CompanyArea;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The areas companies use. An area of a provider belongs to one company at a time; adding it to a second company
 * fails. The methods block and are called off the main thread.
 */
public abstract class AAreaData {
    /**
     * Stores an area of a company.
     *
     * @param area the area; its id is ignored
     * @return the id of the stored area, empty when another company uses the area already
     */
    public abstract Optional<Integer> add(CompanyArea area);

    /**
     * @param id id of an area
     * @return the area, empty when there is none
     */
    public abstract Optional<CompanyArea> area(int id);

    /**
     * @param provider id of a provider
     * @param ref      the provider's reference of an area
     * @return the company's use of the area, empty when no company uses it
     */
    public abstract Optional<CompanyArea> byRef(String provider, String ref);

    /**
     * @param company id of a company
     * @return the areas of the company, the oldest first
     */
    public abstract List<CompanyArea> ofCompany(int company);

    /**
     * @param node a server
     * @return the areas whose world is on the server
     */
    public abstract List<CompanyArea> ofNode(UUID node);

    /**
     * @param player a player
     * @return the areas the player linked to a company
     */
    public abstract List<CompanyArea> linkedBy(UUID player);

    /**
     * Removes an area from its company.
     *
     * @param id id of the area
     * @return true when it was there
     */
    public abstract boolean remove(int id);
}
