/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.services;

import de.eldoria.companies.Companies;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.events.company.CompanyLevelDownEvent;
import de.eldoria.companies.events.company.CompanyLevelUpEvent;
import de.eldoria.companies.events.order.OrderAcceptEvent;
import de.eldoria.companies.events.order.OrderCanceledEvent;
import de.eldoria.companies.events.order.OrderDoneEvent;
import de.eldoria.companies.events.order.OrderExpiredEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides company placeholders for PlaceholderAPI.
 * <p>
 * Values are cached, refreshed on company and order events and periodically for all online players to pick up changes without an event,
 * like renames or newly founded companies.
 */
public class PlaceholderService extends PlaceholderExpansion implements Listener {
    private static final long REFRESH_INTERVAL = 20L * 60L * 5L;
    private final Companies companies;
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Map<UUID, Integer> companyGraph = new ConcurrentHashMap<>();
    private final Map<Integer, CompanyCacheData> companyCache = new ConcurrentHashMap<>();

    public PlaceholderService(Companies companies, ACompanyData companyData, AOrderData orderData) {
        this.companies = companies;
        this.companyData = companyData;
        this.orderData = orderData;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "companies";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", companies.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return companies.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if ("company_name".equalsIgnoreCase(params)) {
            return getCompanyCache(player).map(CompanyCacheData::name).orElse("");
        }

        if ("company_size".equalsIgnoreCase(params)) {
            return String.valueOf(
                    getCompanyCache(player).map(CompanyCacheData::size).orElse(0));
        }

        if ("company_level".equalsIgnoreCase(params)) {
            return String.valueOf(
                    getCompanyCache(player).map(CompanyCacheData::level).orElse(0));
        }

        if ("active_orders".equalsIgnoreCase(params)) {
            return String.valueOf(
                    getCompanyCache(player).map(CompanyCacheData::orderCount).orElse(0));
        }
        return null;
    }

    private Optional<CompanyCacheData> getCompanyCache(OfflinePlayer player) {
        var id = companyGraph.get(player.getUniqueId());
        if (id == null) return Optional.empty();
        return Optional.ofNullable(companyCache.get(id));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshPlayerCache(event.getPlayer());
    }

    private void refreshPlayerCache(OfflinePlayer player) {
        companyData.retrievePlayerCompanyProfile(player).asFuture().thenAccept(company -> {
            if (company.isEmpty()) {
                companyGraph.remove(player.getUniqueId());
                return;
            }
            companyGraph.put(player.getUniqueId(), company.get().id());
            refreshCompanyCache(company.get());
        });
    }

    private void refreshCompanyCache(ICompanyProfile profile) {
        orderData
                .retrieveCompanyOrderCount(profile)
                .asFuture()
                .thenAccept(orders -> companyCache.put(
                        profile.id(),
                        new CompanyCacheData(
                                profile.name(),
                                profile.level(),
                                profile.members().size(),
                                orders)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        companyGraph.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Refreshes the cache of all online players periodically.
     */
    public void startRefreshing() {
        companies
                .getServer()
                .getScheduler()
                .runTaskTimer(
                        companies,
                        () -> companies.getServer().getOnlinePlayers().forEach(this::refreshPlayerCache),
                        REFRESH_INTERVAL,
                        REFRESH_INTERVAL);
    }

    @EventHandler
    public void onCompanyJoin(CompanyJoinEvent event) {
        companyGraph.put(event.player().getUniqueId(), event.company().id());
        loadAndRefreshCompanyCache(event.company());
    }

    private void loadAndRefreshCompanyCache(ICompanyProfile profile) {
        companyData.retrieveCompanyProfile(profile).asFuture().thenAccept(newProfile -> {
            if (newProfile.isEmpty()) {
                companyCache.remove(profile.id());
                return;
            }
            refreshCompanyCache(newProfile.get());
        });
    }

    @EventHandler
    public void onCompanyLeave(CompanyLeaveEvent event) {
        companyGraph.remove(event.player().getUniqueId());
        loadAndRefreshCompanyCache(event.company());
    }

    @EventHandler
    public void onCompanyDisband(CompanyDisbandEvent event) {
        companyCache.remove(event.company().id());
        companyGraph.values().removeIf(id -> id == event.company().id());
    }

    @EventHandler
    public void onOrderAccept(OrderAcceptEvent event) {
        loadAndRefreshCompanyCache(event.company());
    }

    @EventHandler
    public void onOrderDone(OrderDoneEvent event) {
        loadAndRefreshCompanyCache(event.company());
    }

    @EventHandler
    public void onOrderCanceled(OrderCanceledEvent event) {
        loadAndRefreshCompanyCache(event.company());
    }

    @EventHandler
    public void onOrderExpired(OrderExpiredEvent event) {
        loadAndRefreshCompanyCache(event.company());
    }

    @EventHandler
    public void onLevelDown(CompanyLevelDownEvent event) {
        loadAndRefreshCompanyCache(event.company());
    }

    @EventHandler
    public void onLevelUp(CompanyLevelUpEvent event) {
        loadAndRefreshCompanyCache(event.company());
    }

    private record CompanyCacheData(String name, int level, int size, int orderCount) {}
}
