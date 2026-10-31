/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.jobs.hooks;

import de.eldoria.companies.areas.AreaProviders;
import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.data.wrapper.job.Job;
import de.eldoria.companies.data.wrapper.job.JobArea;
import de.eldoria.companies.data.wrapper.job.JobKind;
import de.eldoria.companies.data.wrapper.job.JobLand;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jobs on the land of area providers: the area must lie wholly in one area the owner owns, guarded land without an
 * area refuses it, the working members are made builders without touching who could build before, and the land is
 * watched for a new owner.
 */
class ProviderJobLandTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 31, 12, 0);
    private static final JobArea INSIDE = new JobArea("world", 2, 60, 2, 6, 64, 6);

    private final Land land = new Land();
    private ServerMock server;
    private PlayerMock owner;
    private ProviderJobLand hook;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        owner = server.addPlayer();
        land.owners.add(owner.getUniqueId());
        var providers = new AreaProviders(Logger.getAnonymousLogger());
        providers.register(MockBukkit.createMockPlugin(), land);
        hook = new ProviderJobLand(providers);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anAreaWhollyInAnOwnedClaimIsTheOwnersLand() {
        assertEquals(new Claim.Owned(JobLand.AREA, "land:a"), hook.claim(owner, INSIDE));
    }

    @Test
    void guardedLandPartlyOutsideOrSomeoneElsesClaimRefuses() {
        assertEquals(new Claim.Foreign(null), hook.claim(owner, new JobArea("world", 14, 60, 2, 17, 64, 4)));
        assertEquals(new Claim.Foreign("a"), hook.claim(owner, new JobArea("world", 2, 60, 12, 4, 64, 22)));
        assertEquals(new Claim.None(), hook.claim(owner, new JobArea("world", 30, 60, 30, 32, 64, 32)));
        land.owners.clear();
        assertEquals(new Claim.Foreign("a"), hook.claim(owner, INSIDE));
    }

    @Test
    void workersAreMadeBuildersAndOnlyTheyAreTakenOffAgain() {
        var friend = UUID.randomUUID();
        var worker = UUID.randomUUID();
        land.builders.add(friend);
        var job = job();

        assertEquals(Set.of(worker), hook.planned(job, List.of(owner.getUniqueId(), friend, worker), Set.of()));
        var granted = hook.grant(job, List.of(owner.getUniqueId(), friend, worker), Set.of());
        assertEquals(Set.of(worker), granted);
        assertEquals(Set.of(friend, worker), land.builders);
        assertTrue(hook.revoke(job, granted));
        assertEquals(Set.of(friend), land.builders);
    }

    @Test
    void theLandIsWatchedAndReachesTheWholeClaim() {
        var job = job();

        assertTrue(hook.owns(job));
        assertTrue(hook.reaches(job, "world", 12, 70, 12));
        assertFalse(hook.reaches(job, "world", 25, 70, 12));
        land.owners.clear();
        assertFalse(hook.owns(job));
    }

    @Test
    void theOwnedClaimAPlayerStandsInIsOfferedAsArea() {
        owner.setLocation(new Location(server.getWorld("world"), 5, 64, 5));

        assertEquals(Optional.of(new JobArea("world", 0, -64, 0, 15, 319, 15)), hook.ownArea(owner));
    }

    private Job job() {
        return Job.draft(
                owner.getUniqueId(),
                JobKind.DIG,
                "Pit",
                100,
                5,
                UUID.randomUUID(),
                INSIDE,
                JobLand.AREA,
                "land:a",
                24,
                NOW,
                NOW.plusDays(7));
    }

    /**
     * One claim {@code a} on x and z 0 to 15, a guarded road on x 16 and 17, nothing beyond.
     */
    private static final class Land implements AreaProvider {
        private final Set<UUID> owners = new HashSet<>();
        private final Set<UUID> builders = new HashSet<>();

        @Override
        public String id() {
            return "land";
        }

        @Override
        public Set<AreaCapability> capabilities() {
            return EnumSet.of(AreaCapability.TRUST_LIST, AreaCapability.JOB_LAND);
        }

        @Override
        public List<String> areasAt(Location location) {
            var x = location.getBlockX();
            var z = location.getBlockZ();
            return x >= 0 && x <= 15 && z >= 0 && z <= 15 ? List.of("a") : List.of();
        }

        @Override
        public boolean protects(Location location) {
            var x = location.getBlockX();
            return x == 16 || x == 17;
        }

        @Override
        public boolean exists(String ref) {
            return ref.equals("a");
        }

        @Override
        public Set<UUID> owners(String ref) {
            return Set.copyOf(owners);
        }

        @Override
        public Optional<AreaBounds> bounds(String ref) {
            return Optional.of(new AreaBounds("world", List.of(new AreaBounds.Box(0, -64, 0, 15, 319, 15))));
        }

        @Override
        public String name(String ref) {
            return ref;
        }

        @Override
        public Set<UUID> builders(String ref) {
            return Set.copyOf(builders);
        }

        @Override
        public boolean addBuilder(String ref, UUID player) {
            builders.add(player);
            return true;
        }

        @Override
        public boolean removeBuilder(String ref, UUID player) {
            builders.remove(player);
            return true;
        }
    }
}
