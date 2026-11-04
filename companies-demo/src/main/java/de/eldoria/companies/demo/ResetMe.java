/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import de.eldoria.companies.api.write.CancelOrderResult;
import de.eldoria.companies.api.write.DisbandResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /demo reset-me}: a visitor's clean start. The first call says what would go, the company the visitor owns and
 * their open orders, and what stays; the same call again within {@link #CONFIRM} disbands the company and cancels the
 * orders. A confirmation that would disband another company than the one named asks again. Orders a company or worker
 * took stay with them, and so does a membership in a company someone else owns. After a finished reset the visitor
 * waits for the cooldown before the next one; a reset that could not disband the company, or broke off, needs none.
 * Who asked and who reset is kept in memory only, for this server.
 */
final class ResetMe {
    /**
     * How long a reset waits for its confirmation.
     */
    static final Duration CONFIRM = Duration.ofSeconds(30);

    private final Writes writes;
    private final Clock clock;
    private final Duration cooldown;
    private final Map<UUID, Asked> asked = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> reset = new ConcurrentHashMap<>();

    /**
     * @param writes   the reads and writes through the Companies API
     * @param clock    the time of the confirmation and the cooldown
     * @param cooldown how long after a reset the next one waits; zero for none
     */
    ResetMe(Writes writes, Clock clock, Duration cooldown) {
        this.writes = writes;
        this.clock = clock;
        this.cooldown = cooldown;
    }

    /**
     * Asks to reset, or resets if the player asked within {@link #CONFIRM} about the same company. Blocks on the
     * Companies API, so it must not run on the main thread.
     *
     * @param player the player
     * @return what came of it
     */
    Step request(UUID player) {
        var now = clock.instant();
        var last = reset.get(player);
        if (last != null && last.plus(cooldown).isAfter(now))
            return new Step.Wait(Duration.between(now, last.plus(cooldown)));
        var plan = plan(player);
        var question = asked.remove(player);
        if (question != null
                && question.at().plus(CONFIRM).isAfter(now)
                && question.owned().equals(plan.ownedId())) {
            return new Step.Done(reset(player, plan, now));
        }
        if (plan.ownedId().isEmpty() && plan.open().isEmpty()) return new Step.Nothing(plan);
        asked.put(player, new Asked(now, plan.ownedId()));
        return new Step.Ask(plan);
    }

    private Plan plan(UUID player) {
        return new Plan(writes.company(player), writes.openOrders(player), writes.takenOrders(player));
    }

    private Report reset(UUID player, Plan plan, Instant now) {
        var cancelled = 0;
        var taken = plan.taken();
        for (var order : plan.open()) {
            switch (writes.cancel(order, player)) {
                case CANCELLED -> cancelled++;
                case CLAIMED -> taken++;
                case UNKNOWN, NOT_OWNER -> {}
            }
        }
        var owned = plan.company().filter(Company::owned);
        var disbanded = owned.map(company -> writes.disband(company.id(), player) == DisbandResult.DISBANDED);
        if (disbanded.orElse(true)) reset.put(player, now);
        return new Report(
                owned.filter(company -> disbanded.orElse(false)),
                owned.filter(company -> !disbanded.orElse(false)),
                cancelled,
                taken,
                plan.company().filter(company -> !company.owned()));
    }

    private record Asked(Instant at, Optional<Integer> owned) {}

    /**
     * The company a player is a member of.
     *
     * @param id      id of the company
     * @param name    name of the company
     * @param owned   whether the player owns it
     * @param members how many members it has, the owner included
     */
    record Company(int id, String name, boolean owned, int members) {}

    /**
     * What a reset would do.
     *
     * @param company the player's company, owned or not
     * @param open    ids of the player's orders no one has taken
     * @param taken   how many of the player's orders a company or worker took
     */
    record Plan(Optional<Company> company, List<Integer> open, int taken) {
        /**
         * @return id of the company the player owns, empty when they own none
         */
        Optional<Integer> ownedId() {
            return company.filter(Company::owned).map(Company::id);
        }
    }

    /**
     * What a reset did.
     *
     * @param disbanded the company it disbanded
     * @param kept      the owned company it could not disband
     * @param cancelled how many orders it cancelled
     * @param taken     how many orders stayed because they were taken
     * @param member    the company the player stays a member of, owned by someone else
     */
    record Report(
            Optional<Company> disbanded, Optional<Company> kept, int cancelled, int taken, Optional<Company> member) {}

    /**
     * What came of a request.
     */
    sealed interface Step {
        /**
         * The player has to confirm.
         *
         * @param plan what the reset would do
         */
        record Ask(Plan plan) implements Step {}

        /**
         * The reset ran.
         *
         * @param report what it did
         */
        record Done(Report report) implements Step {}

        /**
         * The player owns no company and has no open orders.
         *
         * @param plan what stays
         */
        record Nothing(Plan plan) implements Step {}

        /**
         * The player reset lately.
         *
         * @param left how long until the next reset
         */
        record Wait(Duration left) implements Step {}
    }

    /**
     * The reads and writes of a reset, through the Companies API. Each call blocks until it is done.
     */
    interface Writes {
        /**
         * @param player id of the player
         * @return the company the player is a member of
         */
        Optional<Company> company(UUID player);

        /**
         * @param player id of the player
         * @return ids of the player's orders no one has taken
         */
        List<Integer> openOrders(UUID player);

        /**
         * @param player id of the player
         * @return how many of the player's orders are taken or delivered and not yet received
         */
        int takenOrders(UUID player);

        /**
         * @param company id of the company
         * @param player  id of its owner
         * @return what came of it
         */
        DisbandResult disband(int company, UUID player);

        /**
         * @param order  id of the order
         * @param player id of its owner
         * @return what came of it
         */
        CancelOrderResult cancel(int order, UUID player);
    }
}
