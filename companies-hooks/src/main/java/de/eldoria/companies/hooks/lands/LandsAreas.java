/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.hooks.lands;

import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.ClaimAreaProvider;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.applicationframework.util.ULID;
import me.angeschossen.lands.api.land.Area;
import me.angeschossen.lands.api.land.Land;
import me.angeschossen.lands.api.land.LandArea;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lands areas as company areas and job land. An area is a Lands area: the default area is the whole land in a world, a
 * sub area its own. It is named {@code world|land/area} by the world and the ids of the land and the area, which stay
 * when players rename them. The owner of the land owns the area; its trusted players are the builders, with the role
 * Lands gives trusted players. Lands moves only whole lands to a new owner and rents areas itself, so its areas are
 * linked, never bought or rented through Companies.
 */
public final class LandsAreas extends ClaimAreaProvider<LandsAreas.Held> {
    private final LandsIntegration lands = LandsIntegration.of(JavaPlugin.getProvidingPlugin(LandsAreas.class));

    /**
     * An area with the world it was found in, as the default area of a land may span several worlds.
     *
     * @param world the name of the world
     * @param area  the area
     */
    public record Held(String world, Area area) {}

    @Override
    public String id() {
        return "lands";
    }

    @Override
    public Set<AreaCapability> capabilities() {
        return EnumSet.of(AreaCapability.LINK_OWNED, AreaCapability.TRUST_LIST, AreaCapability.JOB_LAND);
    }

    @Override
    protected Optional<Held> claimAt(Location location) {
        var world = location.getWorld();
        if (world == null) return Optional.empty();
        return Optional.ofNullable(lands.getArea(location)).map(area -> new Held(world.getName(), area));
    }

    @Override
    protected Optional<Held> claim(String ref) {
        var bar = ref.lastIndexOf('|');
        var slash = ref.lastIndexOf('/');
        if (bar <= 0 || slash <= bar + 1 || slash == ref.length() - 1) return Optional.empty();
        var world = ref.substring(0, bar);
        var landId = ref.substring(bar + 1, slash);
        var areaId = ref.substring(slash + 1);
        var land = land(landId);
        if (land == null) return Optional.empty();
        return land.getAllAreas().stream()
                .filter(area -> String.valueOf(area.getULID()).equals(areaId))
                .findFirst()
                .map(area -> new Held(world, area));
    }

    /**
     * Finds a land by the id {@link #ref} wrote, read back by Lands, or failing that compared as written, so an area
     * never depends on how Lands reads its ids back.
     */
    private @Nullable Land land(String id) {
        var parsed = parse(id);
        var land = parsed == null ? null : lands.getLandByULID(parsed);
        if (land != null) return land;
        return lands.getLands().stream()
                .filter(candidate -> String.valueOf(candidate.getULID()).equals(id))
                .findFirst()
                .orElse(null);
    }

    private static @Nullable ULID parse(String id) {
        try {
            return ULID.fromString(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    protected String ref(Held held) {
        return held.world() + "|" + held.area().getLand().getULID() + "/"
                + held.area().getULID();
    }

    @Override
    protected Set<UUID> claimOwners(Held held) {
        var owner = held.area().getOwnerUID();
        return owner == null ? Set.of() : Set.of(owner);
    }

    @Override
    protected String world(Held held) {
        return held.world();
    }

    @Override
    protected List<AreaBounds.Box> boxes(Held held) {
        var world = Bukkit.getWorld(held.world());
        if (world == null) return List.of();
        int bottom = world.getMinHeight();
        int top = world.getMaxHeight() - 1;
        if (held.area() instanceof LandArea sub) {
            var box = sub.getBoundingBox();
            if (box == null) return List.of();
            var min = box.getMin();
            var max = box.getMax();
            if (min == null || max == null) return List.of();
            return List.of(new AreaBounds.Box(
                    Math.min(min.getX(), max.getX()),
                    Math.min(min.getY(), max.getY()),
                    Math.min(min.getZ(), max.getZ()),
                    Math.max(min.getX(), max.getX()),
                    Math.max(min.getY(), max.getY()),
                    Math.max(min.getZ(), max.getZ())));
        }
        var chunks = held.area().getLand().getChunks(world);
        if (chunks == null) return List.of();
        List<AreaBounds.Box> boxes = new ArrayList<>();
        for (var chunk : chunks) {
            var x = chunk.getX() << 4;
            var z = chunk.getZ() << 4;
            boxes.add(new AreaBounds.Box(x, bottom, z, x + 15, top, z + 15));
        }
        return boxes;
    }

    @Override
    protected String claimName(Held held) {
        var area = held.area();
        return area.isDefault() ? area.getLand().getName() : area.getLand().getName() + " / " + area.getName();
    }

    @Override
    protected Set<UUID> trusted(Held held) {
        Set<UUID> players = new HashSet<>(held.area().getTrustedPlayers());
        players.removeAll(claimOwners(held));
        return players;
    }

    @Override
    protected boolean trust(Held held, UUID player) {
        held.area().trustPlayer(player);
        return held.area().isTrusted(player);
    }

    @Override
    protected boolean untrust(Held held, UUID player) {
        held.area().untrustPlayer(player);
        return !held.area().isTrusted(player);
    }
}
