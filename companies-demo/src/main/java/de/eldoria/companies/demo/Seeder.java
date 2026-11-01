/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import de.eldoria.companies.api.write.FoundRequest;
import de.eldoria.companies.api.write.FoundResult;
import de.eldoria.companies.api.write.OrderLineRequest;
import de.eldoria.companies.api.write.OrderRequest;
import de.eldoria.companies.components.join.JoinResult;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Creates the starting state of the demo through the Companies API in steps, each building on the ones before: the
 * companies of the plan, their members and the orders, all without charging anyone. Every step leaves out what exists
 * already, so a seed that stopped halfway, such as on a database error, is completed on the next start, and a seeded
 * database stays as it is. Blocks; run it off the main thread.
 */
final class Seeder {
    private final Writes writes;
    private final Logger logger;

    /**
     * @param writes what the seed writes through
     * @param logger told what was created and what failed
     */
    Seeder(Writes writes, Logger logger) {
        this.writes = writes;
        this.logger = logger;
    }

    /**
     * Creates what of the plan does not exist yet.
     *
     * @param plan what to create
     * @return how many companies, members and orders were created
     */
    Created seed(SeedPlan plan) {
        var companies = 0;
        var members = 0;
        for (var company : plan.companies()) {
            var id = writes.companyId(company.name());
            if (id.isEmpty()) {
                id = found(company);
                if (id.isEmpty()) continue;
                companies++;
            }
            for (var member : company.members()) {
                if (join(company, id.getAsInt(), member)) members++;
            }
        }
        var orders = 0;
        for (var order : plan.orders()) {
            var owner = DemoPlayers.id(order.owner());
            if (writes.hasOrder(owner, order.name())) continue;
            var refused = writes.createOrder(request(order));
            if (refused.isPresent()) {
                logger.warning(
                        "Could not place the order " + order.name() + " of " + order.owner() + ": " + refused.get());
            } else {
                orders++;
            }
        }
        return new Created(companies, members, orders);
    }

    private OptionalInt found(SeedPlan.Company company) {
        var result = writes.found(new FoundRequest(DemoPlayers.id(company.owner()), company.name(), false));
        if (result instanceof FoundResult.Founded founded)
            return OptionalInt.of(founded.company().id());
        logger.warning("Could not found " + company.name() + ": " + ((FoundResult.Rejected) result).problem());
        return OptionalInt.empty();
    }

    private boolean join(SeedPlan.Company company, int id, String member) {
        var joined = writes.addMember(id, DemoPlayers.id(member));
        if (joined == JoinResult.JOINED) return true;
        if (joined != JoinResult.ALREADY_MEMBER) {
            logger.warning("Could not add " + member + " to " + company.name() + ": " + joined);
        }
        return false;
    }

    private static OrderRequest request(SeedPlan.Order order) {
        var builder = OrderRequest.builder(DemoPlayers.id(order.owner()), order.name())
                .charge(false)
                .respectLimits(false);
        for (var line : order.lines()) {
            builder.line(OrderLineRequest.material(line.material(), line.amount(), line.price()));
        }
        return builder.build();
    }

    /**
     * What a seed created.
     *
     * @param companies companies founded
     * @param members   members added
     * @param orders    orders placed
     */
    record Created(int companies, int members, int orders) {}

    /**
     * The writes of the seed, through the Companies API. Each call blocks until it is done.
     */
    interface Writes {
        /**
         * @param name the name of a company
         * @return its id, empty when no company has the name
         */
        OptionalInt companyId(String name);

        /**
         * @param request the company to found
         * @return what came of it
         */
        FoundResult found(FoundRequest request);

        /**
         * @param company id of the company
         * @param player  id of the player
         * @return what came of it
         */
        JoinResult addMember(int company, UUID player);

        /**
         * @param owner id of the player
         * @param name  the name of an order
         * @return whether the player has an order of that name in any state
         */
        boolean hasOrder(UUID owner, String name);

        /**
         * @param request the order to place
         * @return why it was not created, empty when it was
         */
        Optional<String> createOrder(OrderRequest request);
    }
}
