/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import de.eldoria.companies.api.write.FoundProblem;
import de.eldoria.companies.api.write.FoundRequest;
import de.eldoria.companies.api.write.FoundResult;
import de.eldoria.companies.api.write.OrderRequest;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.join.JoinResult;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeederTest {
    @BeforeEach
    void startServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    private static final SeedPlan PLAN = new SeedPlan(
            List.of(
                    new SeedPlan.Company("Stonebridge", "Demo_Ada", List.of("Demo_Ben")),
                    new SeedPlan.Company("Taken", "Demo_Cleo", List.of())),
            List.of(new SeedPlan.Order(
                    "Demo_Dan", "Building stone", List.of(new SeedPlan.Line(Material.STONE, 256, 0.5)))),
            Map.of(),
            30);

    private final Fake writes = new Fake();
    private final Seeder seeder = new Seeder(writes, Logger.getAnonymousLogger());

    @Test
    void createsCompaniesMembersAndOrdersWithoutCharging() {
        assertEquals(new Seeder.Created(1, 1, 1), seeder.seed(PLAN));

        assertEquals(List.of("Stonebridge"), List.copyOf(writes.companies.keySet()));
        assertEquals(List.of(DemoPlayers.id("Demo_Ben")), writes.members);
        var order = writes.orders.getFirst();
        assertEquals(DemoPlayers.id("Demo_Dan"), order.owner());
        assertFalse(order.charge());
        assertFalse(order.respectLimits());
        assertTrue(writes.charged.isEmpty());
    }

    @Test
    void aSeededDatabaseStaysAsItIs() {
        seeder.seed(PLAN);

        assertEquals(new Seeder.Created(0, 0, 0), seeder.seed(PLAN));

        assertEquals(1, writes.companies.size());
        assertEquals(1, writes.members.size());
        assertEquals(1, writes.orders.size());
    }

    @Test
    void aSeedThatStoppedHalfwayIsCompleted() {
        writes.companies.put("Stonebridge", 7);

        assertEquals(new Seeder.Created(0, 1, 1), seeder.seed(PLAN));

        assertEquals(List.of(DemoPlayers.id("Demo_Ben")), writes.members);
        assertEquals(1, writes.orders.size());
    }

    @Test
    void demoPlayersHaveTheIdsOfAnOfflineServer() {
        assertEquals(UUID.fromString("71646a0b-21ff-316a-84a2-681b0e1b7934"), DemoPlayers.id("Demo_Ada"));
    }

    private static final class Fake implements Seeder.Writes {
        private final Map<String, Integer> companies = new HashMap<>();
        private final List<UUID> members = new ArrayList<>();
        private final List<OrderRequest> orders = new ArrayList<>();
        private final List<UUID> charged = new ArrayList<>();

        @Override
        public OptionalInt companyId(String name) {
            var id = companies.get(name);
            return id == null ? OptionalInt.empty() : OptionalInt.of(id);
        }

        @Override
        public FoundResult found(FoundRequest request) {
            if (request.charge()) charged.add(request.owner());
            if (request.name().equals("Taken")) {
                return new FoundResult.Rejected(FoundProblem.NAME_TAKEN, Component.text("taken"));
            }
            var id = companies.size() + 1;
            companies.put(request.name(), id);
            return new FoundResult.Founded(profile(id));
        }

        @Override
        public JoinResult addMember(int company, UUID player) {
            if (members.contains(player)) return JoinResult.ALREADY_MEMBER;
            members.add(player);
            return JoinResult.JOINED;
        }

        @Override
        public boolean hasOrder(UUID owner, String name) {
            return orders.stream()
                    .anyMatch(
                            order -> order.owner().equals(owner) && order.name().equals(name));
        }

        @Override
        public Optional<String> createOrder(OrderRequest request) {
            orders.add(request);
            return Optional.empty();
        }

        private static ICompanyProfile profile(int id) {
            return (ICompanyProfile) Proxy.newProxyInstance(
                    ICompanyProfile.class.getClassLoader(),
                    new Class<?>[] {ICompanyProfile.class},
                    (proxy, method, args) -> method.getName().equals("id") ? id : null);
        }
    }
}
