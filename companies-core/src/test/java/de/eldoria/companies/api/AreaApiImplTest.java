/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.AreaOfferRequest;
import de.eldoria.companies.api.write.OfferProblem;
import de.eldoria.companies.api.write.OfferResult;
import de.eldoria.companies.api.write.WithdrawResult;
import de.eldoria.companies.areas.AreaOffers;
import de.eldoria.companies.areas.AreaProviders;
import de.eldoria.companies.areas.AreaServices;
import de.eldoria.companies.components.area.AreaBounds;
import de.eldoria.companies.components.area.AreaCapability;
import de.eldoria.companies.components.area.AreaProvider;
import de.eldoria.companies.components.area.AreaSource;
import de.eldoria.companies.components.area.OfferKind;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteAreaData;
import de.eldoria.companies.data.repository.impl.sqlite.SqLiteTestDatabase;
import de.eldoria.companies.data.wrapper.area.CompanyArea;
import de.eldoria.companies.util.TestExecutors;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offering areas of the server through the API: picking the area by location or by reference, the checks of
 * {@code /companyadmin area offer}, listing and withdrawing.
 */
class AreaApiImplTest {
    private static final UUID KEEPER = UUID.randomUUID();

    @TempDir
    Path directory;

    private ExecutorService executor;
    private SqLiteAreaData areas;
    private AreaApiImpl api;
    private World world;
    private Plugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        var server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        SqLiteTestDatabase.create(directory);
        executor = Executors.newFixedThreadPool(2);
        areas = new SqLiteAreaData();
        var providers = new AreaProviders(Logger.getAnonymousLogger());
        var services = new AreaServices(providers, areas, null, null, null, UUID.randomUUID(), () -> "lands");
        var writes = new WriteAccess();
        writes.open(new CompaniesApiImpl.Writes(
                null, null, null, null, null, null, new AreaOffers(services), null, null, Runnable::run));
        api = new AreaApiImpl(providers, areas, writes, Logger.getAnonymousLogger(), executor);
    }

    @AfterEach
    void tearDown() {
        TestExecutors.drain(executor);
        MockBukkit.unmock();
    }

    @Test
    void offersTheAreaAtALocationAndWithdrawsIt() throws Exception {
        api.register(plugin, new Plots(Set.of(AreaCapability.TRANSFER), KEEPER));

        var result = api.offer(AreaOfferRequest.at(new Location(world, 1, 64, 1), OfferKind.RENT, 99.999, 7, "Admin"))
                .get(5, TimeUnit.SECONDS);

        var offer = assertInstanceOf(OfferResult.Offered.class, result).offer();
        assertEquals("plots", offer.provider());
        assertEquals("1;1", offer.ref());
        assertEquals("world", offer.world());
        assertEquals(99.99, offer.price(), 0.0001);
        assertEquals(7, offer.periodDays());
        assertEquals(Optional.of(KEEPER), offer.keeper());
        assertEquals(1, api.offers().get(5, TimeUnit.SECONDS).size());
        assertEquals(
                OfferProblem.ALREADY_OFFERED, problem(AreaOfferRequest.of("plots", "1;1", OfferKind.BUY, 50, 0, "A")));
        assertEquals(WithdrawResult.WITHDRAWN, api.withdraw(offer.id(), "Admin").get(5, TimeUnit.SECONDS));
        assertEquals(WithdrawResult.UNKNOWN, api.withdraw(offer.id(), "Admin").get(5, TimeUnit.SECONDS));
        assertTrue(api.offers().get(5, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void offersAnAreaByItsReference() throws Exception {
        api.register(plugin, new Plots(Set.of(AreaCapability.TRANSFER), KEEPER));

        var result = api.offer(AreaOfferRequest.of("plots", "1;1", OfferKind.BUY, 500, 0, "Admin"))
                .get(5, TimeUnit.SECONDS);

        var offer = assertInstanceOf(OfferResult.Offered.class, result).offer();
        assertEquals(OfferKind.BUY, offer.kind());
        assertEquals("world", offer.world());
        assertEquals(OfferProblem.NO_AREA, problem(AreaOfferRequest.of("plots", "9;9", OfferKind.BUY, 500, 0, "A")));
        assertEquals(OfferProblem.NO_AREA, problem(AreaOfferRequest.of("claims", "1", OfferKind.BUY, 500, 0, "A")));
    }

    @Test
    void namesWhyAnAreaWasNotOffered() throws Exception {
        assertEquals(OfferProblem.NO_AREA, problem(here()));
        api.register(plugin, new Plots(Set.of(), KEEPER));
        assertEquals(OfferProblem.NOT_MOVABLE, problem(here()));
        api.register(plugin, new Plots(Set.of(AreaCapability.TRANSFER), null));
        assertEquals(OfferProblem.NO_KEEPER, problem(here()));
        api.register(plugin, new Plots(Set.of(AreaCapability.TRANSFER), KEEPER));
        areas.add(area(AreaSource.LINKED, 0));
        assertEquals(OfferProblem.IN_USE, problem(here()));
    }

    @Test
    void anOfferACompanyTookCanNotBeWithdrawn() throws Exception {
        api.register(plugin, new Plots(Set.of(AreaCapability.TRANSFER), KEEPER));
        var offer = assertInstanceOf(
                        OfferResult.Offered.class, api.offer(here()).get(5, TimeUnit.SECONDS))
                .offer();
        areas.add(area(AreaSource.BOUGHT, offer.id()));

        assertEquals(WithdrawResult.TAKEN, api.withdraw(offer.id(), "Admin").get(5, TimeUnit.SECONDS));
    }

    @Test
    void anAreaNobodyOwnsIsOfferedWhereItsPluginReleasesToNobody() throws Exception {
        api.register(plugin, new Released());

        var offer = assertInstanceOf(
                        OfferResult.Offered.class, api.offer(here()).get(5, TimeUnit.SECONDS))
                .offer();

        assertTrue(offer.keeper().isEmpty());
    }

    @Test
    void aReferenceIsCheckedLikeALocation() throws Exception {
        api.register(plugin, new Plots(Set.of(), KEEPER));
        assertEquals(OfferProblem.NOT_MOVABLE, problem(AreaOfferRequest.of("plots", "1;1", OfferKind.BUY, 5, 0, "A")));
        api.register(plugin, new Plots(Set.of(AreaCapability.TRANSFER), KEEPER));

        var offer = assertInstanceOf(
                        OfferResult.Offered.class,
                        api.offer(AreaOfferRequest.of("Plots", "1;1", OfferKind.BUY, 5, 0, "A"))
                                .get(5, TimeUnit.SECONDS))
                .offer();

        assertEquals("plots", offer.provider());
        assertEquals(OfferProblem.ALREADY_OFFERED, problem(here()));
    }

    @Test
    void writesFailBeforeTheApiIsOpened() {
        var closed = new AreaApiImpl(
                new AreaProviders(Logger.getAnonymousLogger()),
                areas,
                new WriteAccess(),
                Logger.getAnonymousLogger(),
                executor);

        var offer = assertThrows(
                ExecutionException.class, () -> closed.offer(here()).get(5, TimeUnit.SECONDS));
        var withdraw = assertThrows(
                ExecutionException.class, () -> closed.withdraw(1, "A").get(5, TimeUnit.SECONDS));

        assertInstanceOf(ApiNotReadyException.class, offer.getCause());
        assertInstanceOf(ApiNotReadyException.class, withdraw.getCause());
    }

    @Test
    void requestsNameTheAreaOnceAndValidTerms() {
        var at = new Location(world, 1, 64, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new AreaOfferRequest(at, "plots", "1;1", OfferKind.BUY, 10, 0, "A"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AreaOfferRequest(null, null, null, OfferKind.BUY, 10, 0, "A"));
        assertThrows(IllegalArgumentException.class, () -> AreaOfferRequest.at(at, OfferKind.RENT, 10, 0, "A"));
        assertThrows(IllegalArgumentException.class, () -> AreaOfferRequest.at(at, OfferKind.BUY, -1, 0, "A"));
        assertThrows(
                IllegalArgumentException.class,
                () -> AreaOfferRequest.at(at, OfferKind.BUY, Double.POSITIVE_INFINITY, 0, "A"));
        assertThrows(IllegalArgumentException.class, () -> AreaOfferRequest.at(at, OfferKind.BUY, 2.0E12, 0, "A"));
        assertThrows(IllegalArgumentException.class, () -> AreaOfferRequest.at(at, OfferKind.RENT, 10, 3651, "A"));
    }

    private static CompanyArea area(AreaSource source, int offer) {
        return new CompanyArea(
                0,
                1,
                "plots",
                "1;1",
                "world",
                UUID.randomUUID(),
                source,
                null,
                0,
                null,
                LocalDateTime.now(),
                offer,
                false,
                false,
                false);
    }

    private AreaOfferRequest here() {
        return AreaOfferRequest.at(new Location(world, 1, 64, 1), OfferKind.BUY, 100, 0, "Admin");
    }

    private OfferProblem problem(AreaOfferRequest request) throws Exception {
        return assertInstanceOf(OfferResult.Rejected.class, api.offer(request).get(5, TimeUnit.SECONDS))
                .problem();
    }

    /**
     * A plot plugin whose plot around the origin has no owner, and which can leave an area to nobody.
     */
    private static final class Released implements AreaProvider {
        private final Plots plots = new Plots(Set.of(AreaCapability.TRANSFER), null);

        @Override
        public String id() {
            return plots.id();
        }

        @Override
        public Set<AreaCapability> capabilities() {
            return plots.capabilities();
        }

        @Override
        public List<String> areasAt(Location location) {
            return plots.areasAt(location);
        }

        @Override
        public boolean exists(String ref) {
            return plots.exists(ref);
        }

        @Override
        public Set<UUID> owners(String ref) {
            return Set.of();
        }

        @Override
        public Optional<AreaBounds> bounds(String ref) {
            return plots.bounds(ref);
        }

        @Override
        public String name(String ref) {
            return plots.name(ref);
        }

        @Override
        public boolean releasesToNobody() {
            return true;
        }
    }

    /**
     * A plot plugin with one plot around the origin of {@code world}.
     *
     * @param capabilities what it can do
     * @param owner        the owner of the plot, null for nobody
     */
    private record Plots(
            Set<AreaCapability> capabilities, @Nullable UUID owner) implements AreaProvider {
        @Override
        public String id() {
            return "plots";
        }

        @Override
        public List<String> areasAt(Location location) {
            return Math.abs(location.getBlockX()) < 5 && Math.abs(location.getBlockZ()) < 5
                    ? List.of("1;1")
                    : List.of();
        }

        @Override
        public boolean exists(String ref) {
            return ref.equals("1;1");
        }

        @Override
        public Set<UUID> owners(String ref) {
            return owner == null ? Set.of() : Set.of(owner);
        }

        @Override
        public Optional<AreaBounds> bounds(String ref) {
            return Optional.of(new AreaBounds("world", List.of(new AreaBounds.Box(-4, 0, -4, 4, 255, 4))));
        }

        @Override
        public String name(String ref) {
            return "Plot " + ref;
        }
    }
}
