/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.huskclaims;

import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.ClaimAreaProvider;
import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.api.HuskClaimsAPI;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * HuskClaims claims as company areas and job land. An area is a top-level claim; standing in a child claim means the
 * claim it lies in. HuskClaims gives claims no id, so an area is named {@code world|created|x1;z1;x2;z2} by its world,
 * the time the claim was created in milliseconds ({@code -} for a claim without one) and its corners. The creation
 * time identifies the claim: a claim resized since is found again by it, and stored under its new corners by the sync;
 * a claim without a creation time is only found at its exact corners. The owner of the claim owns the area, an admin
 * claim has none. Builders are the players whose trust level lets them place blocks; Companies gives the lowest such
 * level, such as {@code build}, to players with less or no trust and never lowers a higher trust. A player banned from
 * the claim is never made builder. Changes go through {@link HuskClaimsAPI#editClaimWorld}, which applies them at once
 * and saves them in the background. A claim that changes hands, to a company or back to the server, is moved the way
 * HuskClaims' own transfer moves it and loses every trust, also in its child claims; a claim given back goes to the
 * keeper. HuskClaims gives no way to make a claim an admin claim again, so the server only offers claims that have an
 * owner to keep them. Claim blocks stay with whoever spent them, as with HuskClaims' own
 * transfer.
 */
public final class HuskClaimsAreas extends ClaimAreaProvider<HuskClaimsAreas.Held> {
    /**
     * A claim with the world it lies in, which HuskClaims keeps apart from the claim.
     *
     * @param world the name of the world
     * @param claim the claim
     */
    public record Held(String world, Claim claim) {}

    @Override
    public String id() {
        return "huskclaims";
    }

    @Override
    public Set<AreaCapability> capabilities() {
        return EnumSet.of(
                AreaCapability.LINK_OWNED, AreaCapability.TRUST_LIST, AreaCapability.TRANSFER, AreaCapability.JOB_LAND);
    }

    @Override
    protected Optional<Held> claimAt(Location location) {
        var world = location.getWorld();
        if (world == null) return Optional.empty();
        var position = api().getPosition(location.getX(), location.getY(), location.getZ(), world.getName());
        return api().getClaimAt(position).map(claim -> new Held(world.getName(), claim));
    }

    @Override
    protected Optional<Held> claim(String ref) {
        var parts = ref.split("\\|");
        if (parts.length != 3) return Optional.empty();
        var world = parts[0];
        var corners = parts[2].split(";");
        if (corners.length != 4) return Optional.empty();
        int[] at = new int[4];
        Optional<Long> created;
        try {
            for (var i = 0; i < 4; i++) at[i] = Integer.parseInt(corners[i]);
            created = parts[1].equals("-") ? Optional.empty() : Optional.of(Long.parseLong(parts[1]));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        var claims = claimWorld(world);
        if (claims.isEmpty()) return Optional.empty();
        var old = Region.from(api().getBlockPosition(at[0], at[1]), api().getBlockPosition(at[2], at[3]));
        var exact = claims.get().getParentClaimsOverlapping(old).stream()
                .filter(claim -> claim.getRegion().equals(old) && created(claim).equals(created))
                .findFirst();
        if (exact.isPresent() || created.isEmpty()) return exact.map(claim -> new Held(world, claim));
        var same = claims.get().getClaims().stream()
                .filter(claim -> !claim.isChildClaim() && created(claim).equals(created))
                .toList();
        return same.size() == 1 ? Optional.of(new Held(world, same.getFirst())) : Optional.empty();
    }

    private static Optional<Long> created(Claim claim) {
        return claim.getCreationTime().map(time -> time.toInstant().toEpochMilli());
    }

    @Override
    protected Held topClaim(Held held) {
        var claim = held.claim();
        while (claim.getParent().isPresent()) claim = claim.getParent().get();
        return claim == held.claim() ? held : new Held(held.world(), claim);
    }

    @Override
    protected String ref(Held held) {
        var region = held.claim().getRegion();
        var near = region.getNearCorner();
        var far = region.getFarCorner();
        return held.world() + "|" + created(held.claim()).map(String::valueOf).orElse("-") + "|" + near.getBlockX()
                + ";" + near.getBlockZ() + ";" + far.getBlockX() + ";" + far.getBlockZ();
    }

    @Override
    protected Set<UUID> claimOwners(Held held) {
        return held.claim().getOwner().map(Set::of).orElse(Set.of());
    }

    @Override
    protected String world(Held held) {
        return held.world();
    }

    @Override
    protected List<AreaBounds.Box> boxes(Held held) {
        var world = Bukkit.getWorld(held.world());
        if (world == null) return List.of();
        var region = held.claim().getRegion();
        var near = region.getNearCorner();
        var far = region.getFarCorner();
        return List.of(new AreaBounds.Box(
                Math.min(near.getBlockX(), far.getBlockX()),
                world.getMinHeight(),
                Math.min(near.getBlockZ(), far.getBlockZ()),
                Math.max(near.getBlockX(), far.getBlockX()),
                world.getMaxHeight() - 1,
                Math.max(near.getBlockZ(), far.getBlockZ())));
    }

    @Override
    protected String claimName(Held held) {
        var claims = claimWorld(held.world());
        if (claims.isEmpty()) return ref(held);
        return api().getClaimOwnerName(held.claim(), claims.get()) + " (" + held.world() + ")";
    }

    @Override
    protected Set<UUID> trusted(Held held) {
        Set<String> building = new HashSet<>();
        for (var level : api().getTrustLevels()) {
            if (level.getFlags().contains(OperationType.BLOCK_PLACE)) building.add(level.getId());
        }
        Set<UUID> players = new HashSet<>();
        for (Map.Entry<UUID, String> entry : held.claim().getTrustedUsers().entrySet()) {
            if (building.contains(entry.getValue())) players.add(entry.getKey());
        }
        return players;
    }

    @Override
    protected boolean trust(Held held, UUID player) {
        var level = buildLevel();
        if (level.isEmpty() || held.claim().getBannedUsers().containsKey(player)) return false;
        edit(held, claims -> held.claim().setUserTrustLevel(player, level.get()));
        return level.get().getId().equals(held.claim().getTrustedUsers().get(player));
    }

    @Override
    protected boolean untrust(Held held, UUID player) {
        edit(held, claims -> held.claim().getTrustedUsers().remove(player));
        return !held.claim().getTrustedUsers().containsKey(player);
    }

    @Override
    protected boolean transfer(Held held, UUID player) {
        if (held.claim().getOwner().filter(player::equals).isPresent()) return true;
        moveTo(held, player);
        return held.claim().getOwner().filter(player::equals).isPresent();
    }

    @Override
    protected boolean giveBack(Held held, @Nullable UUID keeper) {
        if (keeper == null) return false;
        moveTo(held, keeper);
        return held.claim().getOwner().filter(keeper::equals).isPresent();
    }

    /**
     * Moves a claim to a new owner as HuskClaims' own transfer does: the owners' claim lists and the map markers follow,
     * the new owner is no longer banned, and every trust is taken off the claim and its child claims. The claim is also
     * taken out of the world's index of claims by owner and put back under the new owner, which HuskClaims' own
     * transfer leaves stale until a restart.
     */
    private void moveTo(Held held, UUID owner) {
        var plugin = api().getPlugin();
        var claim = held.claim();
        edit(held, claims -> {
            plugin.invalidateClaimListCache(claim.getOwner().orElse(null));
            plugin.removeMappedClaim(claim, claims);
            claims.removeClaim(claim);
            clearTrust(claim);
            claim.unBanUser(user(owner));
            claim.setOwner(owner);
            claims.addClaim(claim);
            plugin.addMappedClaim(claim, claims);
            plugin.invalidateClaimListCache(owner);
        });
    }

    private static void clearTrust(Claim claim) {
        claim.getTrustedUsers().clear();
        claim.getTrustedGroups().clear();
        claim.getTrustedTags().clear();
        for (var child : claim.getChildren()) clearTrust(child);
    }

    private void edit(Held held, Consumer<ClaimWorld> change) {
        api().editClaimWorld(api().getWorld(held.world()), change);
    }

    /**
     * @return the trust level Companies gives builders: the lowest that lets a player place blocks
     */
    private static Optional<TrustLevel> buildLevel() {
        return api().getTrustLevels().stream()
                .filter(level -> level.getFlags().contains(OperationType.BLOCK_PLACE))
                .min(Comparator.comparingInt(TrustLevel::getWeight));
    }

    private static Optional<ClaimWorld> claimWorld(String world) {
        return api().getClaimWorld(world);
    }

    private static User user(UUID player) {
        var name = Bukkit.getOfflinePlayer(player).getName();
        return User.of(player, name == null ? player.toString() : name);
    }

    private static HuskClaimsAPI api() {
        return HuskClaimsAPI.getInstance();
    }
}
