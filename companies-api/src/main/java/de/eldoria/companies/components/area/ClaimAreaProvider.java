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
 * An {@link AreaProvider} for a plugin whose land is made of claims, such as plots, claims or lands. A subclass only
 * reaches its plugin's claims; this class turns them into areas the same way for every plugin:
 * <ul>
 *     <li>an area is a top-level claim: a subclaim, such as a subdivision or child claim, stands for the claim it lies
 *     in ({@link #topClaim}), so a company always gets the whole claim;</li>
 *     <li>{@link #canonical(String)} names an area by its top-level claim, also after claims were merged or
 *     resized;</li>
 *     <li>builders are the players trusted to build; a player is only trusted once, and only players are listed, not
 *     groups or public trust;</li>
 *     <li>a claim that can not be found is gone: it has no owners, builders or bounds, and every change to it
 *     fails.</li>
 * </ul>
 * The capabilities a subclass declares decide which of the optional methods it must override: {@link #trusted},
 * {@link #trust} and {@link #untrust} for {@link AreaCapability#TRUST_LIST}, {@link #transfer} and {@link #giveBack}
 * for {@link AreaCapability#TRANSFER}. Every method is called on the main thread, as for any provider.
 *
 * @param <C> the plugin's type of claim
 */
public abstract class ClaimAreaProvider<C> implements AreaProvider {
    /**
     * Creates the provider.
     */
    protected ClaimAreaProvider() {}

    /**
     * Finds the claim at a location.
     *
     * @param location a location
     * @return the innermost claim holding the location, a subclaim where there is one, empty when none does
     */
    protected abstract Optional<C> claimAt(Location location);

    /**
     * Finds a claim by the reference {@link #ref} gave it.
     *
     * @param ref the reference of a claim
     * @return the claim, empty when it is gone or the reference names none
     */
    protected abstract Optional<C> claim(String ref);

    /**
     * Names a top-level claim.
     *
     * @param claim a top-level claim
     * @return its reference, stable for as long as the claim exists
     */
    protected abstract String ref(C claim);

    /**
     * The claim a company gets for a claim found.
     *
     * @param claim a claim, maybe a subclaim
     * @return the top-level claim it lies in, the claim itself when it is one
     */
    protected C topClaim(C claim) {
        return claim;
    }

    /**
     * The owners of a top-level claim.
     *
     * @param claim a top-level claim
     * @return the players owning it, empty when the server holds it
     */
    protected abstract Set<UUID> claimOwners(C claim);

    /**
     * The world of a top-level claim.
     *
     * @param claim a top-level claim
     * @return the name of its world
     */
    protected abstract String world(C claim);

    /**
     * Where a top-level claim lies.
     *
     * @param claim a top-level claim
     * @return the boxes it is made of, full height where the plugin claims whole columns
     */
    protected abstract List<AreaBounds.Box> boxes(C claim);

    /**
     * The name of a top-level claim for display.
     *
     * @param claim a top-level claim
     * @return a name to show players, its reference unless overridden
     */
    protected String claimName(C claim) {
        return ref(claim);
    }

    /**
     * The players trusted to build in a top-level claim. Needed for {@link AreaCapability#TRUST_LIST}.
     *
     * @param claim a top-level claim
     * @return the trusted players, without owners, groups or public trust
     */
    protected Set<UUID> trusted(C claim) {
        return Set.of();
    }

    /**
     * Trusts a player to build in a top-level claim and saves it. Needed for {@link AreaCapability#TRUST_LIST}.
     *
     * @param claim  a top-level claim
     * @param player the player, not trusted yet
     * @return true when the player may build there now
     */
    protected boolean trust(C claim, UUID player) {
        return false;
    }

    /**
     * Takes the trust to build in a top-level claim from a player and saves it. Needed for
     * {@link AreaCapability#TRUST_LIST}.
     *
     * @param claim  a top-level claim
     * @param player the player
     * @return true when the player is no longer trusted there
     */
    protected boolean untrust(C claim, UUID player) {
        return false;
    }

    /**
     * Makes a player the only owner of a top-level claim and saves it. Needed for {@link AreaCapability#TRANSFER}.
     *
     * @param claim  a top-level claim
     * @param player the new owner
     * @return true when the player owns the claim now
     */
    protected boolean transfer(C claim, UUID player) {
        return false;
    }

    /**
     * Gives a top-level claim back to the server: its trusted players are removed and it goes to the keeper. Needed for
     * {@link AreaCapability#TRANSFER}. The claim must never be deleted.
     *
     * @param claim  a top-level claim
     * @param keeper who holds it for the server, null for the server itself where the plugin has such claims
     * @return true when the claim is back with the server
     */
    protected boolean giveBack(C claim, @Nullable UUID keeper) {
        return false;
    }

    private Optional<C> topOf(String ref) {
        return claim(ref).map(this::topClaim);
    }

    @Override
    public List<String> areasAt(Location location) {
        return claimAt(location).map(this::topClaim).map(this::ref).stream().toList();
    }

    @Override
    public String canonical(String ref) {
        return topOf(ref).map(this::ref).orElse(ref);
    }

    @Override
    public boolean exists(String ref) {
        return topOf(ref).isPresent();
    }

    @Override
    public Set<UUID> owners(String ref) {
        return topOf(ref).map(this::claimOwners).orElse(Set.of());
    }

    @Override
    public Optional<AreaBounds> bounds(String ref) {
        return topOf(ref).flatMap(claim -> {
            var boxes = boxes(claim);
            return boxes.isEmpty() ? Optional.empty() : Optional.of(new AreaBounds(world(claim), boxes));
        });
    }

    @Override
    public String name(String ref) {
        return topOf(ref).map(this::claimName).orElse(ref);
    }

    @Override
    public Set<UUID> builders(String ref) {
        return topOf(ref).map(this::trusted).orElse(Set.of());
    }

    @Override
    public boolean addBuilder(String ref, UUID player) {
        var claim = topOf(ref);
        if (claim.isEmpty()) return false;
        return trusted(claim.get()).contains(player) || trust(claim.get(), player);
    }

    @Override
    public boolean removeBuilder(String ref, UUID player) {
        var claim = topOf(ref);
        if (claim.isEmpty()) return false;
        return !trusted(claim.get()).contains(player) || untrust(claim.get(), player);
    }

    @Override
    public boolean transferTo(String ref, UUID player) {
        return topOf(ref).map(claim -> transfer(claim, player)).orElse(false);
    }

    @Override
    public boolean release(String ref, @Nullable UUID keeper) {
        return topOf(ref).map(claim -> giveBack(claim, keeper)).orElse(false);
    }
}
