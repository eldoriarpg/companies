/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.components.area;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reaches the areas of one plot or claim plugin, such as PlotSquared plots or WorldGuard regions, so companies can own
 * them. Companies ships providers for PlotSquared and WorldGuard; other plugins register their own through
 * {@link de.eldoria.companies.api.AreaApi#register(org.bukkit.plugin.Plugin, AreaProvider)}.
 * <p>
 * An area is named by a reference the provider chooses, such as {@code plots|3;-2} or {@code world/spawn-shop}. It is
 * stored together with the provider's {@link #id()}. When the same area can be named in more than one way, as merged
 * plots can, {@link #canonical(String)} names it the one way Companies stores and compares. An area must exist; whether
 * it needs an owner is the provider's choice: PlotSquared counts only claimed plots, while a WorldGuard region may have
 * no owner, as a region the server offers for sale. Every method is called on the main thread. A method may throw
 * when the plugin behind it fails; Companies logs it and treats the area as unavailable for that call.
 * <p>
 * The methods of {@link AreaCapability#TRUST_LIST} and {@link AreaCapability#TRANSFER} do nothing by default, so a
 * provider without those capabilities need not write them.
 */
public interface AreaProvider {
    /**
     * The id of the provider.
     *
     * @return the stable id of the provider, lower case, such as {@code plotsquared}
     */
    String id();

    /**
     * The capabilities of the provider.
     *
     * @return what the provider can do with its areas
     */
    Set<AreaCapability> capabilities();

    /**
     * Finds the areas at a location, the one that decides there first, such as the region with the highest priority.
     * The order is the same on every call for the same areas.
     *
     * @param location a location
     * @return the references of the areas holding the location, empty when none does
     */
    List<String> areasAt(Location location);

    /**
     * Finds the area that decides at a location.
     *
     * @param location a location
     * @return the reference of the first of {@link #areasAt(Location)}, empty when no area holds the location
     */
    default Optional<String> at(Location location) {
        return areasAt(location).stream().findFirst();
    }

    /**
     * Names an area the one way Companies stores it.
     *
     * @param ref the reference of an area, as stored or as found
     * @return the reference that names the area now, the same reference when there is one name only
     */
    default String canonical(String ref) {
        return ref;
    }

    /**
     * Whether the plugin guards a location that lies in none of its areas, such as a road between plots or a plot nobody
     * claimed. A job is refused there, as nobody may hand that land out.
     *
     * @param location a location
     * @return true when the location is the plugin's land without an area of a player on it
     */
    default boolean protects(Location location) {
        return false;
    }

    /**
     * Whether an area still exists.
     *
     * @param ref the reference of an area
     * @return whether the area still exists, as the provider counts areas
     */
    boolean exists(String ref);

    /**
     * The owners of an area.
     *
     * @param ref the reference of an area
     * @return the players owning the area, empty when nobody does or the area is gone
     */
    Set<UUID> owners(String ref);

    /**
     * Whether a player owns an area.
     *
     * @param player a player
     * @param ref    the reference of an area
     * @return whether the player owns the area
     */
    default boolean isOwner(UUID player, String ref) {
        return owners(ref).contains(player);
    }

    /**
     * Where an area lies.
     *
     * @param ref the reference of an area
     * @return where the area lies, empty when it is gone
     */
    Optional<AreaBounds> bounds(String ref);

    /**
     * The name of an area for display.
     *
     * @param ref the reference of an area
     * @return a name of the area to show players, such as the plot id with its world or the region id
     */
    String name(String ref);

    /**
     * The builders of an area.
     *
     * @param ref the reference of an area
     * @return the players who may build in the area without owning it
     */
    default Set<UUID> builders(String ref) {
        return Set.of();
    }

    /**
     * Lets a player build in an area. Needs {@link AreaCapability#TRUST_LIST}.
     *
     * @param ref    the reference of an area
     * @param player the player
     * @return true when the player may build there now
     */
    default boolean addBuilder(String ref, UUID player) {
        return false;
    }

    /**
     * Takes the right to build in an area from a player who does not own it. Needs {@link AreaCapability#TRUST_LIST}.
     *
     * @param ref    the reference of an area
     * @param player the player
     * @return true when the player may no longer build there through the provider's builder list
     */
    default boolean removeBuilder(String ref, UUID player) {
        return false;
    }

    /**
     * Makes a player the only owner of an area. Needs {@link AreaCapability#TRANSFER}.
     *
     * @param ref    the reference of an area
     * @param player the new owner
     * @return true when the player owns the area now
     */
    default boolean transferTo(String ref, UUID player) {
        return false;
    }

    /**
     * Gives an area back to the server: its builders are removed and it goes to the keeper, such as the server account
     * an offer names. An area is never deleted or opened for anyone to claim. Needs {@link AreaCapability#TRANSFER}.
     *
     * @param ref    the reference of an area
     * @param keeper who holds the area for the server, null for nobody where the plugin allows an area without owner
     * @return true when the area is back with the server
     */
    default boolean release(String ref, @Nullable UUID keeper) {
        return false;
    }
}
