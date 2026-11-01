/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import org.bukkit.Bukkit;

/**
 * Entry point of the Companies API.
 * <p>
 * Companies registers its implementation with Bukkit's {@link org.bukkit.plugin.ServicesManager} while it enables. A
 * plugin that declares Companies as {@code depend} (or {@code softdepend} and checks that it is enabled) can obtain it
 * in its own {@code onEnable} with {@link #get()} or with {@code getServicesManager().load(CompaniesApi.class)}.
 * <p>
 * <b>Threading.</b> Every method of the API returns a {@link java.util.concurrent.CompletableFuture} that completes off
 * the main thread. Do not block the main thread on it with {@code join()} or {@code get()}. Run Bukkit calls that need
 * the main thread through the scheduler, for example with
 * {@code future.thenAcceptAsync(result -> ..., runnable -> Bukkit.getScheduler().runTask(plugin, runnable))}.
 * <p>
 * <b>Reads and writes.</b> Reads work as soon as the API is registered. Write operations need the economy, which
 * Companies sets up once the server has started; before that, and while Companies runs without an economy, they
 * complete exceptionally with an {@link ApiNotReadyException}.
 * <p>
 * <b>Anonymity.</b> Companies keeps orders anonymous: players never learn who placed an order they work on, and owners
 * never learn which company works on theirs. The API and its events expose both sides. Use them for logic, logging or
 * staff tools, and never show the owner of an order to the members of a company or the company to the owner.
 * <p>
 * <b>Multiple servers.</b> All data lives in the shared database, so reads see the changes of every server. Events are
 * fired only on the server where the action happened.
 */
public interface CompaniesApi {
    /**
     * The running API.
     *
     * @return the registered API
     * @throws IllegalStateException when Companies is not enabled
     */
    static CompaniesApi get() {
        var api = Bukkit.getServicesManager().load(CompaniesApi.class);
        if (api == null) throw new IllegalStateException("Companies is not enabled");
        return api;
    }

    /**
     * Access to companies and their members.
     *
     * @return company API
     */
    CompanyApi companies();

    /**
     * Access to orders, material prices and order creation.
     *
     * @return order API
     */
    OrderApi orders();

    /**
     * Access to the per-player data of Companies, such as items waiting to be picked up.
     *
     * @return player API
     */
    PlayerApi players();

    /**
     * Access to the company ranking.
     *
     * @return ranking API
     */
    RankingApi ranking();

    /**
     * Access to server events: time-limited tax reductions, reward multipliers and fee reductions.
     *
     * @return server event API
     */
    ServerEventApi events();

    /**
     * Access to the plot and claim plugins companies can own areas of.
     *
     * @return area API
     */
    AreaApi areas();

    /**
     * Access to the shop plugins Companies reads prices from.
     *
     * @return price API
     */
    PriceApi prices();

    /**
     * Access to jobs: reading them and posting new ones.
     *
     * @return job API
     */
    JobApi jobs();
}
