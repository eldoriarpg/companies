package de.eldoria.companies.services;

import de.eldoria.companies.Companies;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.events.company.CompanyJoinEvent;
import de.eldoria.companies.events.company.CompanyLeaveEvent;
import de.eldoria.companies.events.company.CompanyLevelDownEvent;
import de.eldoria.companies.events.company.CompanyLevelUpEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlaceholderService extends PlaceholderExpansion implements Listener {
    private final Companies companies;
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Map<UUID, Integer> companyGraph = new HashMap<>();
    private final Map<Integer, CompanyCacheData> companyCache = new HashMap<>();

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
        return String.join(", ", companies.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return companies.getDescription().getVersion();
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if ("company_name".equalsIgnoreCase(params)) {
            return getCompanyCache(player).map(CompanyCacheData::name).orElse(null);
        }

        if ("company_size".equalsIgnoreCase(params)) {
            return String.valueOf(getCompanyCache(player).map(CompanyCacheData::size).orElse(0));
        }

        if ("company_level".equalsIgnoreCase(params)) {
            return String.valueOf(getCompanyCache(player).map(CompanyCacheData::level).orElse(0));
        }

        if ("active_orders".equalsIgnoreCase(params)) {
            return String.valueOf(getCompanyCache(player).map(CompanyCacheData::orderCount).orElse(0));
        }
        return null;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refreshPlayerCache(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        companyGraph.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onCompanyJoin(CompanyJoinEvent event) {
        loadAndRefreshCompanyCache(event.company());
    }

    @EventHandler
    public void onCompanyLeave(CompanyLeaveEvent event) {
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

    private void refreshPlayerCache(OfflinePlayer player) {
        companyData.retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenAccept(company -> {
                    if (company.isEmpty()) {
                        companyGraph.remove(player.getUniqueId());
                        return;
                    }
                    companyGraph.put(player.getUniqueId(), company.get().id());

                    if (companyCache.containsKey(company.get().id())) return;
                    refreshCompanyCache(company.get());
                });
    }

    private void loadAndRefreshCompanyCache(ICompanyProfile profile) {
        companyData.retrieveCompanyProfile(profile)
                .asFuture()
                .thenAccept(newProfile -> {
                    if(newProfile.isEmpty()){
                        companyCache.remove(profile.id());
                        return;
                    }
                    refreshCompanyCache(newProfile.get());
                });
    }

    private void refreshCompanyCache(ICompanyProfile profile) {
        orderData.retrieveCompanyOrderCount(profile)
                .asFuture()
                .thenAccept(orders -> {
                    companyCache.put(profile.id(), new CompanyCacheData(profile.name(), profile.level(), profile.members().size(), orders));
                });

    }

    private static class CompanyCacheData {
        private final String name;
        private final int level;
        private final int size;
        private final int orderCount;

        private CompanyCacheData(String name, int level, int size, int orderCount) {
            this.name = name;
            this.level = level;
            this.size = size;
            this.orderCount = orderCount;
        }

        public String name() {
            return name;
        }

        public int size() {
            return size;
        }

        public int orderCount() {
            return orderCount;
        }

        public int level() {
            return level;
        }
    }

    private Optional<CompanyCacheData> getCompanyCache(OfflinePlayer player) {
        var id = companyGraph.get(player.getUniqueId());
        if (id == null) return Optional.empty();
        return Optional.ofNullable(companyCache.get(id));
    }
}
