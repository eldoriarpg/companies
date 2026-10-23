/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.PlaceholderSettings;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.eldoutilities.localization.ILocalizer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * The placeholder caches, the expansion and the periodic refresh, wired together.
 */
public final class Placeholders {
    private static final long TICKS_PER_SECOND = 20L;

    private final Plugin plugin;
    private final PlaceholderSettings settings;
    private final PlayerPlaceholderCache players;
    private final CompanyPlaceholderCache companies;
    private final LeaderboardCache leaderboards;
    private final PlaceholderRegistry registry;

    private Placeholders(
            Plugin plugin,
            PlaceholderSettings settings,
            PlaceholderLoader loader,
            Economy economy,
            ILocalizer localizer) {
        this.plugin = plugin;
        this.settings = settings;
        this.players = new PlayerPlaceholderCache(loader::player, settings.offlineCacheSize(), settings.refresh());
        this.companies = new CompanyPlaceholderCache(loader::company, settings.offlineCacheSize(), settings.refresh());
        this.leaderboards = new LeaderboardCache(loader::ranking, settings.leaderboardSize());
        this.registry = new PlaceholderRegistry(
                players, companies, leaderboards, economy::format, localizer::localize, settings::loadingText);
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
            Economy economy,
            ILocalizer localizer) {
        var loader =
                new PlaceholderLoader(plugin.getServer(), configuration, companyData, orderData, stats, treasuries);
        return new Placeholders(plugin, configuration.placeholderSettings(), loader, economy, localizer);
    }

    /**
     * Registers the expansion, keeps the players already online and starts the periodic refresh.
     */
    public void start() {
        new CompaniesExpansion(plugin, registry).register();
        plugin.getServer().getOnlinePlayers().stream().map(Player::getUniqueId).forEach(players::join);
        var period = settings.refresh().toSeconds() * TICKS_PER_SECOND;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 1L, period);
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
    }
}
