/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.PlaceholderSettings;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOfferData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.util.Threading;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * The placeholder caches, the expansion and the periodic refresh, wired together.
 * <p>
 * The settings are read again where they are used, so a reloaded refresh interval, leaderboard size and loading text
 * apply; {@link #reload()} restarts the periodic refresh with the new interval. The size of the offline caches is read
 * once. Changes announced by other nodes reload the affected values at once.
 */
public final class Placeholders {
    private static final long TICKS_PER_SECOND = 20L;

    private final Plugin plugin;
    private final Supplier<PlaceholderSettings> settings;
    private final PlayerPlaceholderCache players;
    private final CompanyPlaceholderCache companies;
    private final LeaderboardCache leaderboards;
    private final OfferPlaceholderCache offers;
    private final PlaceholderRegistry registry;
    private BukkitTask refreshTask;

    private Placeholders(
            Plugin plugin,
            Supplier<PlaceholderSettings> settings,
            PlaceholderLoader loader,
            AOfferData offerData,
            Economy economy,
            ILocalizer localizer) {
        this.plugin = plugin;
        this.settings = settings;
        var cacheSize = settings.get().offlineCacheSize();
        this.players = new PlayerPlaceholderCache(
                loader::player, cacheSize, () -> settings.get().refresh());
        this.companies = new CompanyPlaceholderCache(
                loader::company, cacheSize, () -> settings.get().refresh());
        this.leaderboards =
                new LeaderboardCache(loader::ranking, () -> settings.get().leaderboardSize());
        this.offers =
                new OfferPlaceholderCache(() -> CompletableFuture.supplyAsync(offerData::running, Threading.VIRTUAL));
        this.registry = new PlaceholderRegistry(
                players, companies, leaderboards, offers, economy::format, localizer::localize, () -> settings.get()
                        .loadingText());
    }

    /**
     * Builds the placeholders from the configuration.
     *
     * @param plugin        the plugin
     * @param configuration configuration with the placeholder settings
     * @param companyData   company repository
     * @param orderData     order repository
     * @param stats         statistics repository
     * @param treasuries    treasury repository
     * @param offerData     event offer repository
     * @param economy       economy formatting money
     * @param localizer     localizer of the plugin
     * @return the placeholders, not yet started
     */
    public static Placeholders create(
            Plugin plugin,
            Configuration configuration,
            ACompanyData companyData,
            AOrderData orderData,
            AStatsData stats,
            ATreasuryData treasuries,
            AOfferData offerData,
            Economy economy,
            ILocalizer localizer) {
        var loader =
                new PlaceholderLoader(plugin.getServer(), configuration, companyData, orderData, stats, treasuries);
        return new Placeholders(plugin, configuration::placeholderSettings, loader, offerData, economy, localizer);
    }

    /**
     * Registers the expansion, keeps the players already online and starts the periodic refresh.
     */
    public void start() {
        new CompaniesExpansion(plugin, registry).register();
        plugin.getServer().getOnlinePlayers().stream().map(Player::getUniqueId).forEach(players::join);
        scheduleRefresh();
    }

    /**
     * Restarts the periodic refresh with the current interval, refreshing once right away.
     */
    public void reload() {
        scheduleRefresh();
    }

    /**
     * Reloads a company and its members, after a node changed it. Thread safe.
     *
     * @param company id of the company
     */
    public void companyChanged(int company) {
        onMainThread(() -> {
            companies.invalidate(company);
            players.invalidateCompany(company);
        });
    }

    /**
     * Reloads a player, after a node changed the orders the player owns or works on alone. Thread safe.
     *
     * @param player uuid of the player
     */
    public void playerChanged(UUID player) {
        onMainThread(() -> players.invalidate(player));
    }

    /**
     * Reloads the leaderboards, after a node changed the ranking. Thread safe.
     */
    public void rankingChanged() {
        onMainThread(leaderboards::refresh);
    }

    /**
     * Reloads the event offers, after one started or closed. Thread safe.
     */
    public void offersChanged() {
        offers.refresh();
    }

    /**
     * Reloads every value, after changes of other nodes may have been missed. Thread safe.
     */
    public void refreshAll() {
        onMainThread(this::refresh);
    }

    private void onMainThread(Runnable task) {
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private void scheduleRefresh() {
        if (refreshTask != null) refreshTask.cancel();
        var period = settings.get().refresh().toSeconds() * TICKS_PER_SECOND;
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 1L, period);
    }

    /**
     * @return the listener keeping the caches current
     */
    public Listener events() {
        return new PlaceholderEvents(players, companies);
    }

    private void refresh() {
        players.refreshOnline();
        companies.refreshAll();
        leaderboards.refresh();
        offers.refresh();
    }
}
