/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Companies kept in memory, with the number of orders each works on.
 */
final class FakeCompanies implements AlertCompanies {
    private final Map<Integer, CompanyProfile> companies = new HashMap<>();
    private final Map<Integer, Integer> accepted = new HashMap<>();

    /**
     * @param company company to add
     * @return these companies
     */
    FakeCompanies add(CompanyProfile company) {
        companies.put(company.id(), company);
        return this;
    }

    /**
     * @param company id of the company
     * @param orders  orders it works on
     */
    void accepted(int company, int orders) {
        accepted.put(company, orders);
    }

    @Override
    public CompletableFuture<Optional<CompanyProfile>> profileOf(OfflinePlayer player) {
        return CompletableFuture.completedFuture(companies.values().stream()
                .filter(company -> company.isMember(player.getUniqueId()))
                .findFirst());
    }

    @Override
    public CompletableFuture<Optional<CompanyProfile>> profile(int company) {
        return CompletableFuture.completedFuture(Optional.ofNullable(companies.get(company)));
    }

    @Override
    public CompletableFuture<Integer> acceptedOrders(CompanyProfile company) {
        return CompletableFuture.completedFuture(accepted.getOrDefault(company.id(), 0));
    }
}
