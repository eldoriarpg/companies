/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AHistoryData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.data.wrapper.history.HistoryEntry;
import de.eldoria.companies.data.wrapper.order.FullOrder;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Looks orders up for admins, whoever owns or works on them: one order with the names of both sides, its workers,
 * when it runs out and its history, and pages of the orders of a player or a company. Used by
 * {@code /companyadmin order} and the dashboard's admin pages alike. The methods block on the database.
 */
public final class AdminOrderLookup {
    private final AOrderData orders;
    private final ACompanyData companies;
    private final AHistoryData history;
    private final Configuration configuration;
    private final Function<UUID, String> names;

    /**
     * @param orders        order repository
     * @param companies     company repository
     * @param history       order history
     * @param configuration the plugin's configuration, for delivery and removal times
     * @param names         the name of a player by id, the id where none is known
     */
    public AdminOrderLookup(
            AOrderData orders,
            ACompanyData companies,
            AHistoryData history,
            Configuration configuration,
            Function<UUID, String> names) {
        this.orders = orders;
        this.companies = companies;
        this.history = history;
        this.configuration = configuration;
        this.names = names;
    }

    /**
     * When an order runs out.
     *
     * @param at      the moment, as stored
     * @param removal true when an open order is removed then, false when a claimed order is due
     */
    public record Deadline(LocalDateTime at, boolean removal) {}

    /**
     * An order with the names of both sides and of its workers.
     *
     * @param order       the order with its deliveries
     * @param ownerName   name of the owner
     * @param companyName name of the company working on it, empty while nobody or a single player claimed it
     * @param deadline    when the order runs out, empty once it is delivered
     * @param names       names of the workers, including a solo worker who delivered nothing yet, by id
     * @param history     history entries of the order
     */
    public record Details(
            FullOrder order,
            String ownerName,
            Optional<String> companyName,
            Optional<Deadline> deadline,
            Map<UUID, String> names,
            List<HistoryEntry> history) {
        public Details {
            names = Map.copyOf(names);
            history = List.copyOf(history);
        }
    }

    /**
     * An order of a page with the names of its owner and of who claimed it.
     *
     * @param order        the order
     * @param ownerName    name of the owner
     * @param claimantName name of the company or solo worker that claimed it, empty while open
     */
    public record Row(SimpleOrder order, String ownerName, Optional<String> claimantName) {}

    /**
     * @param id id of the order
     * @return the order with its names, empty for an unknown order
     */
    public Optional<Details> details(int id) {
        var found = orders.retrieveOrderById(id).join();
        if (found == null || found.isEmpty()) return Optional.empty();
        var order = orders.toFullOrder(found.get());
        Map<UUID, String> workers = new HashMap<>();
        for (var content : order.contents()) {
            for (var worker : content.workerAmount().keySet()) workers.computeIfAbsent(worker, names);
        }
        order.soloWorker().ifPresent(worker -> workers.computeIfAbsent(worker, names));
        return Optional.of(new Details(
                order,
                names.apply(order.owner()),
                companyName(order),
                deadline(order),
                workers,
                history.retrieveByOrder(id).join()));
    }

    /**
     * One page of the orders of a player or a company, newest first.
     *
     * @param owner   the owner, or null for the orders of a company
     * @param company the company, or null for the orders of a player
     * @param page    number of the page, from one
     * @param size    orders on a page
     * @return the orders of the page
     */
    public List<Row> page(@Nullable UUID owner, @Nullable Integer company, int page, int size) {
        Map<Integer, Optional<String>> claimants = new HashMap<>();
        var found = orders.retrieveOrdersForAdmin(owner, company, Math.max(1, page), size)
                .join();
        if (found == null) return List.of();
        return found.stream()
                .map(order -> new Row(order, names.apply(order.owner()), claimantName(order, claimants)))
                .toList();
    }

    private Optional<String> claimantName(SimpleOrder order, Map<Integer, Optional<String>> claimants) {
        if (order.state() == OrderState.UNCLAIMED) return Optional.empty();
        var soloWorker = order.soloWorker();
        if (soloWorker.isPresent()) return Optional.of(names.apply(soloWorker.get()));
        return claimants.computeIfAbsent(order.company(), id -> companyName(order));
    }

    private Optional<Deadline> deadline(SimpleOrder order) {
        return switch (order.state()) {
            case CLAIMED ->
                Optional.of(new Deadline(
                        order.claimed().plus(order.deliveryHours(configuration), ChronoUnit.HOURS), false));
            case UNCLAIMED ->
                Optional.of(new Deadline(
                        order.claimed().plus(configuration.orderSetting().maxUnclaimedHours(), ChronoUnit.HOURS),
                        true));
            case DELIVERED, RECEIVED -> Optional.empty();
        };
    }

    private Optional<String> companyName(SimpleOrder order) {
        if (order.state() == OrderState.UNCLAIMED || order.isSolo()) return Optional.empty();
        var company = companies.retrieveCompanyById(order.company()).join();
        if (company == null) return Optional.empty();
        return Optional.of(company.map(SimpleCompany::name).orElse("#" + order.company()));
    }
}
