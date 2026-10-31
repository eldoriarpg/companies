/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.demo;

import de.eldoria.companies.api.ApiNotReadyException;
import de.eldoria.companies.api.CompaniesApi;
import de.eldoria.companies.api.write.CreateOrderResult;
import de.eldoria.companies.api.write.FoundRequest;
import de.eldoria.companies.api.write.FoundResult;
import de.eldoria.companies.api.write.OrderRequest;
import de.eldoria.companies.components.join.JoinResult;
import de.eldoria.companies.components.order.OrderState;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

/**
 * The helper of the Companies demo network. On the server whose config has {@code seed.enabled}, it creates the
 * starting state of the demo after every nightly reset, which empties the Companies database. On each start it first
 * writes the market history, which has no API, over a connection to the Companies database of its own
 * ({@code database}), so it lands before the plugin's own recording sweeps; then it completes companies, members and
 * orders through the API. Companies accepts writes only once its economy is set up, so that part waits and tries again
 * every few seconds; any other failure is logged and the next start completes what is missing.
 */
public final class CompaniesDemo extends JavaPlugin {
    private static final long RETRY_TICKS = 20L * 10;
    private static final int ATTEMPTS = 30;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("seed.enabled")) return;
        var section = getConfig().getConfigurationSection("seed");
        var plan = SeedPlan.read(section == null ? new MemoryConfiguration() : section, getLogger()::warning);
        MariaDbMarket store;
        try {
            store = MariaDbMarket.of(
                    getConfig().getString("database.host", "localhost"),
                    getConfig().getInt("database.port", 3306),
                    getConfig().getString("database.database", "companies"),
                    getConfig().getString("database.user", "root"),
                    getConfig().getString("database.password", ""));
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "The database address of the market history is invalid", e);
            return;
        }
        var market = new MarketSeeder(store, Clock.systemUTC());
        getServer().getScheduler().runTaskAsynchronously(this, () -> seedMarket(market, plan));
        attempt(new Seeder(new ApiWrites(), getLogger()), plan, 1);
    }

    private void seedMarket(MarketSeeder market, SeedPlan plan) {
        try {
            var rows = market.seed(plan.market(), plan.marketDays());
            if (rows > 0) {
                getLogger()
                        .info("Seeded the market history: %d materials, %d rows."
                                .formatted(plan.market().size(), rows));
            }
        } catch (RuntimeException e) {
            getLogger().log(Level.SEVERE, "Could not seed the market history", e);
        }
    }

    private void attempt(Seeder seeder, SeedPlan plan, int attempt) {
        getServer()
                .getScheduler()
                .runTaskLaterAsynchronously(
                        this,
                        () -> {
                            try {
                                var created = seeder.seed(plan);
                                if (created.companies() + created.members() + created.orders() > 0) {
                                    getLogger()
                                            .info("Seeded %d companies, %d members and %d orders."
                                                    .formatted(
                                                            created.companies(), created.members(), created.orders()));
                                }
                            } catch (RuntimeException e) {
                                var cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                                if (cause instanceof ApiNotReadyException && attempt < ATTEMPTS) {
                                    attempt(seeder, plan, attempt + 1);
                                    return;
                                }
                                getLogger()
                                        .log(
                                                Level.SEVERE,
                                                "Could not seed the demo; the next start completes it",
                                                cause);
                            }
                        },
                        RETRY_TICKS);
    }

    /**
     * The writes of the seed through the registered Companies API.
     */
    private static final class ApiWrites implements Seeder.Writes {
        @Override
        public OptionalInt companyId(String name) {
            return CompaniesApi.get()
                    .companies()
                    .byName(name)
                    .join()
                    .map(company -> OptionalInt.of(company.id()))
                    .orElse(OptionalInt.empty());
        }

        @Override
        public FoundResult found(FoundRequest request) {
            return CompaniesApi.get().companies().found(request).join();
        }

        @Override
        public JoinResult addMember(int company, UUID player) {
            return CompaniesApi.get().companies().addMember(company, player).join();
        }

        @Override
        public boolean hasOrder(UUID owner, String name) {
            return CompaniesApi.get().orders().byOwner(owner, OrderState.UNCLAIMED, OrderState.RECEIVED).join().stream()
                    .anyMatch(order -> order.name().equals(name));
        }

        @Override
        public Optional<String> createOrder(OrderRequest request) {
            return switch (CompaniesApi.get().orders().create(request).join()) {
                case CreateOrderResult.Created created -> Optional.empty();
                case CreateOrderResult.Rejected rejected ->
                    Optional.of(rejected.reason().name());
            };
        }
    }
}
