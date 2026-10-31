/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.data.repository;

import de.eldoria.companies.data.wrapper.area.AreaOffer;
import de.eldoria.companies.data.wrapper.area.CompanyArea;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The areas companies use. An area of a provider belongs to one company at a time; adding it to a second company
 * fails. Deleting a company leaves its areas: the server of an area takes the builders Companies gave it off and drops
 * it then, which it can not do once the rows are gone. The methods block and are called off the main thread.
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
     * Removes an area from its company, with the builders Companies gave it.
     *
     * @param id id of the area
     * @return true when it was there
     */
    public abstract boolean remove(int id);

    /**
     * Stores the reference an area goes by now, as its provider names it after plots were merged or unmerged.
     *
     * @param id  id of the area
     * @param ref the reference
     * @return true when it was stored; false when another company uses the area under that reference
     */
    public abstract boolean rename(int id, String ref);

    /**
     * @param area id of an area
     * @return the players Companies gave the right to build there, which it takes back again
     */
    public abstract Set<UUID> builders(int area);

    /**
     * Notes that Companies gave a player the right to build in an area.
     *
     * @param area   id of the area
     * @param player the player
     */
    public abstract void addBuilder(int area, UUID player);

    /**
     * Notes that Companies took the right to build in an area from a player.
     *
     * @param area   id of the area
     * @param player the player
     */
    public abstract void removeBuilder(int area, UUID player);

    /**
     * Stores an offer of the server.
     *
     * @param offer the offer
     * @return id of the offer, empty when the area is offered already
     */
    public abstract Optional<Integer> addOffer(AreaOffer offer);

    /**
     * @param id id of an offer
     * @return the offer, empty when there is none
     */
    public abstract Optional<AreaOffer> offer(int id);

    /**
     * @param provider id of the area provider
     * @param ref      the provider's reference of the area
     * @return the offer of the area, empty when it is not offered
     */
    public abstract Optional<AreaOffer> offerOf(String provider, String ref);

    /**
     * @return every offer, oldest first
     */
    public abstract List<AreaOffer> offers();

    /**
     * @return the offers no company has, oldest first
     */
    public abstract List<AreaOffer> openOffers();

    /**
     * Withdraws an offer no company has.
     *
     * @param id id of the offer
     * @return true when it was withdrawn; false when it is not there or a company has the area
     */
    public abstract boolean removeOffer(int id);

    /**
     * @return the rented areas whose rent has not ended, the one due first first
     */
    public abstract List<CompanyArea> rents();

    /**
     * Notes that a rent was paid for another period. Only changes the area while it is still paid until {@code from}
     * and renews, so a period is never stored twice and a rent cancelled meanwhile is not renewed.
     *
     * @param id    id of the area
     * @param from  until when it was paid
     * @param until until when it is paid now
     * @return true when it was stored
     */
    public abstract boolean renewed(int id, LocalDateTime from, LocalDateTime until);

    /**
     * Notes that the company was told that the rent of an area is due soon.
     *
     * @param id id of the area
     */
    public abstract void rentWarned(int id);

    /**
     * Ends the rent of an area, which then goes back to the server.
     *
     * @param id id of the area
     * @return true when it was rented and its rent had not ended
     */
    public abstract boolean endRent(int id);

    /**
     * Lets the rent of an area run out at the end of the period paid.
     *
     * @param id id of the area
     * @return true when it was renewing
     */
    public abstract boolean stopRenewing(int id);
}
