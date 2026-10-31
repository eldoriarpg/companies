/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.ClaimAreaProvider;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The base of claim providers: a subclaim stands for its top-level claim, trust is given once and only to players,
 * a gone claim answers nothing and refuses every change, and giving back goes through the subclass.
 */
class ClaimAreaProviderTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    private final Claims claims = new Claims();

    @Test
    void aSubclaimStandsForItsTopLevelClaim() {
        var top = claims.add("1", null);
        claims.add("2", top);

        assertEquals("1", claims.canonical("2"));
        assertEquals(Set.of(owner), claims.owners("2"));
        assertTrue(claims.exists("2"));
        assertEquals(
                Optional.of(new AreaBounds("world", List.of(new AreaBounds.Box(0, -64, 0, 15, 319, 15)))),
                claims.bounds("2"));
    }

    @Test
    void aPlayerIsTrustedOnceAndOnlyTakenOffWhenTrusted() {
        var top = claims.add("1", null);

        assertTrue(claims.addBuilder("1", player));
        assertTrue(claims.addBuilder("1", player));
        assertEquals(1, top.trusts);
        assertEquals(Set.of(player), claims.builders("1"));
        assertTrue(claims.removeBuilder("1", player));
        assertTrue(claims.removeBuilder("1", player));
        assertTrue(claims.builders("1").isEmpty());
    }

    @Test
    void aGoneClaimAnswersNothingAndRefusesChanges() {
        assertFalse(claims.exists("9"));
        assertEquals("9", claims.canonical("9"));
        assertTrue(claims.owners("9").isEmpty());
        assertTrue(claims.bounds("9").isEmpty());
        assertFalse(claims.addBuilder("9", player));
        assertFalse(claims.transferTo("9", player));
        assertFalse(claims.release("9", null));
    }

    @Test
    void transferAndGiveBackGoThroughTheTopLevelClaim() {
        var top = claims.add("1", null);
        claims.add("2", top);
        claims.addBuilder("2", player);

        assertTrue(claims.transferTo("2", player));
        assertEquals(Set.of(player), top.owners);
        assertTrue(claims.release("2", owner));
        assertEquals(Set.of(owner), top.owners);
        assertTrue(top.trusted.isEmpty());
    }

    private final class Claim {
        private final String id;
        private final @Nullable Claim parent;
        private final Set<UUID> owners = new HashSet<>(Set.of(owner));
        private final Set<UUID> trusted = new HashSet<>();
        private int trusts;

        private Claim(String id, @Nullable Claim parent) {
            this.id = id;
            this.parent = parent;
        }
    }

    private final class Claims extends ClaimAreaProvider<Claim> {
        private final Map<String, Claim> stored = new HashMap<>();

        private Claim add(String id, @Nullable Claim parent) {
            var claim = new Claim(id, parent);
            stored.put(id, claim);
            return claim;
        }

        @Override
        public String id() {
            return "claims";
        }

        @Override
        public Set<AreaCapability> capabilities() {
            return EnumSet.allOf(AreaCapability.class);
        }

        @Override
        protected Optional<Claim> claimAt(Location location) {
            return Optional.empty();
        }

        @Override
        protected Optional<Claim> claim(String ref) {
            return Optional.ofNullable(stored.get(ref));
        }

        @Override
        protected Claim topClaim(Claim claim) {
            return claim.parent == null ? claim : claim.parent;
        }

        @Override
        protected String ref(Claim claim) {
            return claim.id;
        }

        @Override
        protected Set<UUID> claimOwners(Claim claim) {
            return Set.copyOf(claim.owners);
        }

        @Override
        protected String world(Claim claim) {
            return "world";
        }

        @Override
        protected List<AreaBounds.Box> boxes(Claim claim) {
            return List.of(new AreaBounds.Box(0, -64, 0, 15, 319, 15));
        }

        @Override
        protected Set<UUID> trusted(Claim claim) {
            return Set.copyOf(claim.trusted);
        }

        @Override
        protected boolean trust(Claim claim, UUID player) {
            claim.trusts++;
            return claim.trusted.add(player);
        }

        @Override
        protected boolean untrust(Claim claim, UUID player) {
            return claim.trusted.remove(player);
        }

        @Override
        protected boolean transfer(Claim claim, UUID player) {
            claim.owners.clear();
            claim.owners.add(player);
            return true;
        }

        @Override
        protected boolean giveBack(Claim claim, @Nullable UUID keeper) {
            claim.trusted.clear();
            claim.owners.clear();
            if (keeper != null) claim.owners.add(keeper);
            return true;
        }
    }
}
