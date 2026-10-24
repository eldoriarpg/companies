/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services.alerts;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import org.bukkit.OfflinePlayer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Reads the companies that own alerts, with their members.
 */
public interface AlertCompanies {
    /**
     * @param player a player
     * @return the company of the player, empty when they have none
     */
    CompletableFuture<Optional<CompanyProfile>> profileOf(OfflinePlayer player);

    /**
     * @param company id of the company
     * @return the company, empty when it does not exist
     */
    CompletableFuture<Optional<CompanyProfile>> profile(int company);

    /**
     * @param company the company
     * @return the orders the company works on, or null when they could not be counted
     */
    CompletableFuture<Integer> acceptedOrders(CompanyProfile company);

    /**
     * @param companyData company repository
     * @param orderData   order repository
     * @return companies read from the repositories
     */
    static AlertCompanies of(ACompanyData companyData, AOrderData orderData) {
        return new AlertCompanies() {
            @Override
            public CompletableFuture<Optional<CompanyProfile>> profileOf(OfflinePlayer player) {
                return companyData.retrievePlayerCompanyProfile(player).asFuture();
            }

            @Override
            public CompletableFuture<Optional<CompanyProfile>> profile(int company) {
                return companyData
                        .retrieveCompanyById(company)
                        .asFuture()
                        .thenCompose(found -> found.isEmpty()
                                ? CompletableFuture.completedFuture(Optional.empty())
                                : companyData
                                        .retrieveCompanyProfile(found.get())
                                        .asFuture());
            }

            @Override
            public CompletableFuture<Integer> acceptedOrders(CompanyProfile company) {
                return orderData.retrieveCompanyOrderCount(company).asFuture();
            }
        };
    }
}
