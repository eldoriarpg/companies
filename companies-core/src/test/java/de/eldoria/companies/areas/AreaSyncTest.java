/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.areas;

import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.components.area.AreaSource;
import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAreaData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.area.AreaOffer;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import de.eldoria.companies.data.wrapper.company.CompanyDetails;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sync of company areas: builders follow the members with {@code BUILD} without touching what the owner gave, a
 * linked area goes with the member it is tied to and with its company, merged plots are stored under their new name,
 * and the container rule narrows what members may.
 */
class AreaSyncTest {
    private static final UUID NODE = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final String REF = "plots|1;1";

    @TempDir
    Path directory;

    private final UUID owner = UUID.randomUUID();
    private final UUID builder = UUID.randomUUID();
    private final UUID worker = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();
    private final UUID keeper = UUID.randomUUID();
    private final Plot plot = new Plot();
    private final List<CompanyMember> members = new ArrayList<>();
    private ServerMock server;
    private WorldMock world;
    private SqLiteAreaData areas;
    private AreaProviders providers;
    private AreaSync sync;
    private int company;
    private boolean gone;
    private final AtomicLong errors = new AtomicLong();
    private Instant now = Instant.parse("2026-10-31T12:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("plots");
        SqLiteTestDatabase.create(directory);
        query("INSERT INTO companies(name) VALUES ('Builders')").single().insert();
        company = query("SELECT id FROM companies WHERE name = 'Builders'")
                .single()
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        areas = new SqLiteAreaData();
        providers = new AreaProviders(Logger.getAnonymousLogger());
        providers.register(MockBukkit.createMockPlugin(), plot);
        members.add(CompanyMember.of(company, owner, CompanyPermission.OWNER.mask()));
        members.add(CompanyMember.of(company, builder, CompanyPermission.BUILD.mask()));
        members.add(CompanyMember.of(company, worker, 0));
        plot.owners.add(owner);
        plot.builders.add(friend);
        sync = new AreaSync(
                providers,
                areas,
                id -> gone
                        ? Optional.empty()
                        : Optional.of(new CompanyProfile(
                                company,
                                "Builders",
                                LocalDateTime.of(2026, 1, 1, 0, 0),
                                1,
                                CompanyDetails.NONE,
                                List.copyOf(members))),
                errors::get,
                () -> now,
                NODE,
                Runnable::run,
                Logger.getAnonymousLogger(),
                ignored -> {});
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void thePlanAddsWhoMayBuildAndTakesOffOnlyWhatCompaniesAdded() {
        var plan = AreaSync.plan(Set.of(owner, builder), Set.of(owner), Set.of(friend), Set.of(worker));

        assertEquals(Set.of(builder), plan.add());
        assertEquals(Set.of(worker), plan.remove());
    }

    @Test
    void buildersFollowTheMembersWithBuildAndTheOwnersFriendStays() {
        var id = linked();

        sync.check();

        assertEquals(Set.of(friend, builder), plot.builders);
        assertEquals(Set.of(builder), areas.builders(id));
        members.set(1, CompanyMember.of(company, builder, 0));
        sync.check();
        assertEquals(Set.of(friend), plot.builders);
        assertEquals(Set.of(), areas.builders(id));
    }

    @Test
    void aLinkedAreaGoesWithTheMemberItIsTiedTo() {
        var id = linked();
        sync.check();
        members.removeIf(member -> member.uuid().equals(owner));

        sync.check();
        sync.check();
        assertTrue(areas.area(id).isPresent());
        later();
        sync.check();

        assertTrue(areas.area(id).isEmpty());
        assertEquals(Set.of(friend), plot.builders);
    }

    @Test
    void theAreasOfACompanyThatIsGoneAreGivenBack() {
        var id = linked();
        sync.check();
        gone = true;

        sync.check();
        later();
        sync.check();

        assertTrue(areas.area(id).isEmpty());
        assertFalse(plot.builders.contains(builder));
    }

    @Test
    void aCheckThatSawAQueryFailChangesNothing() {
        var id = linked();
        sync.check();
        gone = true;
        AreaSync failing = new AreaSync(
                providers,
                areas,
                ignored -> {
                    errors.incrementAndGet();
                    return Optional.empty();
                },
                errors::get,
                () -> now,
                NODE,
                Runnable::run,
                Logger.getAnonymousLogger(),
                ignored -> {});

        failing.check();
        later();
        failing.check();

        assertTrue(areas.area(id).isPresent());
        assertTrue(plot.builders.contains(builder));
    }

    @Test
    void theAreasOfAWorldThatIsNotLoadedAreLeftAlone() {
        var id = linked();
        sync.check();
        server.unloadWorld(world, false);
        gone = true;

        sync.check();
        later();
        sync.check();

        assertTrue(areas.area(id).isPresent());
    }

    @Test
    void twoAreasThatBecameOneKeepOneWithTheBuildersOfBoth() {
        var first = linked();
        var second = linked("plots|2;1");
        areas.addBuilder(second, worker);

        sync.check();

        assertTrue(areas.area(first).isPresent());
        assertTrue(areas.area(second).isEmpty());
        assertEquals(Set.of(builder, worker), areas.builders(first));
    }

    @Test
    void anAreaNamedAnotherWayIsStoredUnderItsNewName() {
        var id = linked();
        plot.canonical = "plots|0;1";

        sync.check();

        assertEquals("plots|0;1", areas.area(id).orElseThrow().ref());
    }

    @Test
    void theContainerRuleLetsOnlyMembersWithContainersAndOwnersIn() {
        linked();
        sync.check();
        var rule = new AreaContainers(providers, sync, player -> {});
        var spot = new Location(world, 1, 64, 1);

        assertTrue(rule.allowed(server.addPlayer(), spot));
        var member = server.addPlayer();
        members.add(CompanyMember.of(company, member.getUniqueId(), CompanyPermission.BUILD.mask()));
        var trusted = server.addPlayer();
        members.add(CompanyMember.of(company, trusted.getUniqueId(), CompanyPermission.CONTAINERS.mask()));
        sync.check();

        assertFalse(rule.allowed(member, spot));
        assertTrue(rule.allowed(trusted, spot));
    }

    @Test
    void aBoughtAreaBelongsToTheCompanyOwnerAndOnlyItsBuildersBuild() {
        plot.owners.clear();
        plot.owners.add(keeper);
        fromServer(AreaOffer.Kind.BUY);

        sync.check();

        assertEquals(Set.of(owner), plot.owners);
        assertEquals(Set.of(builder), plot.builders);
    }

    @Test
    void anEndedRentGoesBackToTheKeeperAtOnce() {
        var id = fromServer(AreaOffer.Kind.RENT);
        sync.check();
        areas.endRent(id);

        sync.check();

        assertTrue(areas.area(id).isEmpty());
        assertEquals(Set.of(keeper), plot.owners);
        assertTrue(plot.builders.isEmpty());
    }

    @Test
    void anEndedRentOfAnAreaThatIsGoneIsDropped() {
        var id = fromServer(AreaOffer.Kind.RENT);
        sync.check();
        areas.endRent(id);
        plot.exists = false;

        sync.check();
        assertTrue(areas.area(id).isPresent());
        later();
        sync.check();

        assertTrue(areas.area(id).isEmpty());
    }

    @Test
    void aBoughtAreaOfACompanyThatIsGoneGoesBackToTheKeeper() {
        var id = fromServer(AreaOffer.Kind.BUY);
        sync.check();
        gone = true;

        sync.check();
        assertTrue(areas.area(id).isPresent());
        later();
        sync.check();

        assertTrue(areas.area(id).isEmpty());
        assertEquals(Set.of(keeper), plot.owners);
    }

    private int fromServer(AreaOffer.Kind kind) {
        var offer = areas.addOffer(new AreaOffer(
                        0,
                        "plotsquared",
                        REF,
                        "plots",
                        NODE,
                        kind,
                        100,
                        kind == AreaOffer.Kind.RENT ? 7 : 0,
                        keeper,
                        LocalDateTime.of(2026, 10, 31, 12, 0)))
                .orElseThrow();
        return areas.add(new CompanyArea(
                        0,
                        company,
                        "plotsquared",
                        REF,
                        "plots",
                        NODE,
                        kind.source(),
                        null,
                        100,
                        kind == AreaOffer.Kind.RENT ? LocalDateTime.of(2026, 11, 7, 12, 0) : null,
                        LocalDateTime.of(2026, 10, 31, 12, 0),
                        offer,
                        true,
                        false,
                        false))
                .orElseThrow();
    }

    private void later() {
        now = now.plusSeconds(AreaSync.CHECK_SECONDS);
    }

    private int linked() {
        return linked(REF);
    }

    private int linked(String ref) {
        return areas.add(new CompanyArea(
                        0,
                        company,
                        "plotsquared",
                        ref,
                        "plots",
                        NODE,
                        AreaSource.LINKED,
                        owner,
                        0,
                        null,
                        LocalDateTime.of(2026, 10, 31, 12, 0)))
                .orElseThrow();
    }

    /**
     * One plot everywhere, owned and trusted in memory.
     */
    private static final class Plot implements AreaProvider {
        private final Set<UUID> owners = new HashSet<>();
        private final Set<UUID> builders = new HashSet<>();
        private String canonical = REF;
        private boolean exists = true;

        @Override
        public String id() {
            return "plotsquared";
        }

        @Override
        public Set<AreaCapability> capabilities() {
            return EnumSet.of(AreaCapability.LINK_OWNED, AreaCapability.TRUST_LIST, AreaCapability.TRANSFER);
        }

        @Override
        public List<String> areasAt(Location location) {
            return List.of(canonical);
        }

        @Override
        public String canonical(String ref) {
            return canonical;
        }

        @Override
        public boolean exists(String ref) {
            return exists;
        }

        @Override
        public Set<UUID> owners(String ref) {
            return Set.copyOf(owners);
        }

        @Override
        public Optional<AreaBounds> bounds(String ref) {
            return Optional.empty();
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

        @Override
        public boolean transferTo(String ref, UUID player) {
            owners.clear();
            owners.add(player);
            return true;
        }

        @Override
        public boolean release(String ref, UUID keeper) {
            builders.clear();
            owners.clear();
            if (keeper != null) owners.add(keeper);
            return true;
        }
    }
}
