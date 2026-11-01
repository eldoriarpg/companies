/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.placeholders;

import de.eldoria.companies.components.company.CompanyPermission;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.data.repository.AAreaData;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AHomeData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.wrapper.area.CompanyHome;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.order.ReturnKind;
import de.eldoria.companies.data.wrapper.treasury.Treasury;
import de.eldoria.companies.orders.OrderLimits;
import de.eldoria.companies.util.Times;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Loads the placeholder values of players and companies and the complete rankings from the repositories.
 * <p>
 * The order limit of an online player honours the limit permissions; offline the permissions are unknown and the
 * configured limit is used.
 */
public final class PlaceholderLoader {
    private final Server server;
    private final Configuration configuration;
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final AStatsData stats;
    private final ATreasuryData treasuries;
    private final AAreaData areas;
    private final AHomeData homes;
    private final Executor executor;
    private final OrderLimits limits;

    /**
     * @param server        server to look up players
     * @param configuration configuration with the levels and the order limit
     * @param companyData   company repository
     * @param orderData     order repository
     * @param stats         statistics repository
     * @param treasuries    treasury repository
     * @param areas         the areas of companies
     * @param homes         the homes of companies
     * @param executor      reads the areas and homes, whose repositories block
     */
    public PlaceholderLoader(
            Server server,
            Configuration configuration,
            ACompanyData companyData,
            AOrderData orderData,
            AStatsData stats,
            ATreasuryData treasuries,
            AAreaData areas,
            AHomeData homes,
            Executor executor) {
        this.server = server;
        this.configuration = configuration;
        this.companyData = companyData;
        this.orderData = orderData;
        this.stats = stats;
        this.treasuries = treasuries;
        this.areas = areas;
        this.homes = homes;
        this.executor = executor;
        this.limits = new OrderLimits(orderData, configuration::userSettings);
    }

    /**
     * Loads the values of a player.
     *
     * @param uuid id of the player
     * @return the values
     */
    public CompletableFuture<Optional<PlayerView>> player(UUID uuid) {
        var online = server.getPlayer(uuid);
        var limit = online == null ? configuration.userSettings().maxOrders() : limits.limit(online);
        OfflinePlayer player = online == null ? server.getOfflinePlayer(uuid) : online;
        var company = companyData.retrievePlayerCompanyProfile(player).asFuture();
        var orders = orderData.retrievePlayerOrderCount(player).asFuture();
        var returned =
                orderData.retrieveItemReturnCount(uuid, ReturnKind.WORKER).asFuture();
        var pending = orderData.retrieveItemReturnCount(uuid, ReturnKind.OWNER).asFuture();
        var soloActive = orderData.retrieveSoloOrderCount(uuid).asFuture();
        var soloCompleted = orderData.retrieveSoloCompletedCount(uuid).asFuture();
        return CompletableFuture.allOf(company, orders, returned, pending, soloActive, soloCompleted)
                .thenApply(done -> Optional.of(new PlayerView(
                        uuid,
                        company.join()
                                .map(profile -> OptionalInt.of(profile.id()))
                                .orElseGet(OptionalInt::empty),
                        company.join().flatMap(profile -> role(profile, player)),
                        count(orders.join()),
                        limit,
                        count(returned.join()) + count(pending.join()),
                        count(soloActive.join()),
                        count(soloCompleted.join()))));
    }

    /**
     * Loads the values of a company.
     *
     * @param id id of the company
     * @return the values, or empty when the company does not exist
     */
    public CompletableFuture<Optional<CompanyView>> company(int id) {
        return companyData
                .retrieveCompanyById(id)
                .asFuture()
                .thenCompose(company -> company.isEmpty()
                        ? CompletableFuture.completedFuture(Optional.<CompanyProfile>empty())
                        : companyData.retrieveCompanyProfile(company.get()).asFuture())
                .thenCompose(profile -> profile.isEmpty()
                        ? CompletableFuture.completedFuture(Optional.empty())
                        : company(profile.get()).thenApply(Optional::of));
    }

    private CompletableFuture<CompanyView> company(CompanyProfile profile) {
        var companyStats = companyData.retrieveCompanyStats(profile).asFuture();
        var active = orderData.retrieveCompanyOrderCount(profile).asFuture();
        var treasury = treasuries.retrieveTreasury(profile.id());
        var places = CompletableFuture.supplyAsync(() -> places(profile.id()), executor);
        return CompletableFuture.allOf(companyStats, active, treasury, places)
                .thenApply(done -> view(profile, companyStats.join(), active.join(), treasury.join(), places.join()));
    }

    private Places places(int company) {
        var server = homes.home(company).map(CompanyHome::server).orElse("");
        return new Places(areas.ofCompany(company).size(), server);
    }

    /**
     * The areas and home of a company, as the placeholders show them.
     *
     * @param areas  how many areas it uses
     * @param server proxy name of the server of its home
     */
    private record Places(int areas, String server) {}

    private CompanyView view(
            CompanyProfile profile, CompanyStats values, Integer orders, Treasury treasury, Places places) {
        return new CompanyView(
                profile.id(),
                profile.name(),
                profile.level(),
                configuration
                        .companySettings()
                        .level(profile.level())
                        .map(CompanyLevel::levelName)
                        .orElse(""),
                profile.members().size(),
                count(orders),
                owner(profile),
                profile.tag(),
                profile.color(),
                values.orderCount(),
                values.price(),
                values.deliveredItems(),
                treasury.balance().doubleValue(),
                configuration.companySettings().treasury().effectiveShare(treasury.payoutShare()),
                places.areas(),
                places.server());
    }

    /**
     * Loads every ranked company of a ranking.
     *
     * @param order ordering
     * @param range time range, ending now
     * @return the ranking, best first
     */
    public CompletableFuture<List<CompanyRank>> ranking(TopOrder order, TimeRange range) {
        return stats.retrieveRanking(order, range, Times.now(), 1, Integer.MAX_VALUE);
    }

    private static Optional<Role> role(CompanyProfile profile, OfflinePlayer player) {
        return profile.member(player).map(member -> member.isOwner() ? Role.OWNER : Role.MEMBER);
    }

    private static String owner(CompanyProfile profile) {
        return profile.members(CompanyPermission.OWNER).stream()
                .findFirst()
                .map(CompanyMember::name)
                .orElse("");
    }

    private static int count(Integer value) {
        return Objects.requireNonNullElse(value, 0);
    }
}
