/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.griefprevention;

import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.ClaimAreaProvider;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * GriefPrevention claims as company areas and job land. An area is a top-level claim, named by its claim id; standing
 * in a subdivision means the claim it lies in, which is also the only kind GriefPrevention moves to a new owner. The
 * owner of the claim owns the area, an admin claim has none. Builders are the players with build trust; Companies
 * gives build trust and takes it back. GriefPrevention keeps one trust per player, so a player the owner gave container
 * or access trust gets build trust while Companies wants them to build and none after. A claim that changes hands, to
 * a company or back to the server, loses every trust, so nobody the old owner trusted keeps access. A claim given back
 * goes to the keeper, or becomes an admin claim without one. A claim counts against the claim blocks of whoever owns
 * it, so a company owner who gets a claim may have fewer blocks left to claim with. Claims are looked up by id through
 * a small cache, as GriefPrevention searches every claim for an id.
 */
public final class GriefPreventionAreas extends ClaimAreaProvider<Claim> {
    private final Map<Long, Claim> cache = new HashMap<>();

    @Override
    public String id() {
        return "griefprevention";
    }

    @Override
    public Set<AreaCapability> capabilities() {
        return EnumSet.of(
                AreaCapability.LINK_OWNED, AreaCapability.TRUST_LIST, AreaCapability.TRANSFER, AreaCapability.JOB_LAND);
    }

    @Override
    protected Optional<Claim> claimAt(Location location) {
        return Optional.ofNullable(store().getClaimAt(location, true, null));
    }

    @Override
    protected Optional<Claim> claim(String ref) {
        long id;
        try {
            id = Long.parseLong(ref);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        var cached = cache.get(id);
        if (cached != null && cached.inDataStore && Long.valueOf(id).equals(cached.getID())) return Optional.of(cached);
        cache.remove(id);
        var claim = store().getClaim(id);
        if (claim == null || !claim.inDataStore) return Optional.empty();
        cache.put(id, claim);
        return Optional.of(claim);
    }

    @Override
    protected Claim topClaim(Claim claim) {
        var top = claim;
        while (top.parent != null) top = top.parent;
        return top;
    }

    @Override
    protected String ref(Claim claim) {
        return String.valueOf(claim.getID());
    }

    @Override
    protected Set<UUID> claimOwners(Claim claim) {
        var owner = claim.isAdminClaim() ? null : claim.getOwnerID();
        return owner == null ? Set.of() : Set.of(owner);
    }

    @Override
    protected String world(Claim claim) {
        var world = claim.getLesserBoundaryCorner().getWorld();
        return world == null ? "" : world.getName();
    }

    @Override
    protected List<AreaBounds.Box> boxes(Claim claim) {
        var lesser = claim.getLesserBoundaryCorner();
        var greater = claim.getGreaterBoundaryCorner();
        var world = lesser.getWorld();
        if (world == null) return List.of();
        return List.of(new AreaBounds.Box(
                lesser.getBlockX(),
                world.getMinHeight(),
                lesser.getBlockZ(),
                greater.getBlockX(),
                world.getMaxHeight() - 1,
                greater.getBlockZ()));
    }

    @Override
    protected String claimName(Claim claim) {
        return claim.isAdminClaim() ? "#" + claim.getID() : claim.getOwnerName() + " #" + claim.getID();
    }

    @Override
    protected Set<UUID> trusted(Claim claim) {
        var builders = new ArrayList<String>();
        claim.getPermissions(builders, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        Set<UUID> players = new HashSet<>();
        for (var entry : builders) {
            try {
                players.add(UUID.fromString(entry));
            } catch (IllegalArgumentException e) {
                continue;
            }
        }
        return players;
    }

    @Override
    protected boolean trust(Claim claim, UUID player) {
        claim.setPermission(player.toString(), ClaimPermission.Build);
        store().saveClaim(claim);
        return true;
    }

    @Override
    protected boolean untrust(Claim claim, UUID player) {
        claim.dropPermission(player.toString());
        store().saveClaim(claim);
        return true;
    }

    @Override
    protected boolean transfer(Claim claim, UUID player) {
        if (player.equals(claim.getOwnerID())) return true;
        store().changeClaimOwner(claim, player);
        if (!player.equals(claim.getOwnerID())) return false;
        clearTrust(claim);
        return true;
    }

    @Override
    protected boolean giveBack(Claim claim, @Nullable UUID keeper) {
        store().changeClaimOwner(claim, keeper);
        var moved = keeper == null ? claim.isAdminClaim() : keeper.equals(claim.getOwnerID());
        if (moved) clearTrust(claim);
        return moved;
    }

    /**
     * Takes every trust off a claim that changed hands, only once the change happened, as another plugin may cancel it.
     */
    private static void clearTrust(Claim claim) {
        claim.clearPermissions();
        store().saveClaim(claim);
    }

    private static DataStore store() {
        return GriefPrevention.instance.dataStore;
    }
}
