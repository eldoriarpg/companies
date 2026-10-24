/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.company.ICompanyStats;
import de.eldoria.companies.components.company.IMemberStats;
import de.eldoria.companies.components.company.ITreasury;
import de.eldoria.companies.components.join.JoinResult;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import org.bukkit.OfflinePlayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reads companies from the repositories and adds members through the membership service.
 */
final class CompanyApiImpl implements CompanyApi {
    private final ACompanyData companyData;
    private final AStatsData stats;
    private final ATreasuryData treasuries;
    private final WriteAccess writes;
    private final Function<UUID, OfflinePlayer> players;
    private final Supplier<LocalDateTime> clock;
    private final Executor executor;

    CompanyApiImpl(
            ACompanyData companyData,
            AStatsData stats,
            ATreasuryData treasuries,
            WriteAccess writes,
            Function<UUID, OfflinePlayer> players,
            Supplier<LocalDateTime> clock,
            Executor executor) {
        this.companyData = companyData;
        this.stats = stats;
        this.treasuries = treasuries;
        this.writes = writes;
        this.players = players;
        this.clock = clock;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Optional<? extends ICompanyProfile>> byId(int id) {
        return profile(() -> companyData.retrieveCompanyById(id).asFuture().join());
    }

    @Override
    public CompletableFuture<Optional<? extends ICompanyProfile>> byName(String name) {
        return profile(() -> companyData.retrieveCompanyByName(name).asFuture().join());
    }

    @Override
    public CompletableFuture<Optional<? extends ICompanyProfile>> byTag(String tag) {
        return profile(() -> companyData.retrieveCompanyByTag(tag).asFuture().join());
    }

    @Override
    public CompletableFuture<Optional<? extends ICompanyProfile>> of(UUID player) {
        return CompletableFuture.<Optional<? extends ICompanyProfile>>supplyAsync(
                () -> companyData
                        .retrievePlayerCompanyProfile(players.apply(player))
                        .asFuture()
                        .join(),
                executor);
    }

    @Override
    public CompletableFuture<Optional<? extends ICompanyStats>> stats(int company) {
        return CompletableFuture.<Optional<? extends ICompanyStats>>supplyAsync(
                () -> companyData.retrieveCompanyById(company).asFuture().join().map(found -> companyData
                        .retrieveCompanyStats(found)
                        .asFuture()
                        .join()),
                executor);
    }

    @Override
    public CompletableFuture<List<? extends IMemberStats>> memberStats(int company, TimeRange range) {
        return CompletableFuture.<List<? extends IMemberStats>>supplyAsync(
                () -> stats.retrieveMemberStats(
                                company, range.start(clock.get()).orElse(null))
                        .join(),
                executor);
    }

    @Override
    public CompletableFuture<Optional<? extends ITreasury>> treasury(int company) {
        return CompletableFuture.<Optional<? extends ITreasury>>supplyAsync(
                () -> companyData.retrieveCompanyById(company).asFuture().join().map(found -> treasuries
                        .retrieveTreasury(found.id())
                        .join()),
                executor);
    }

    @Override
    public CompletableFuture<JoinResult> addMember(int company, UUID player) {
        return writes.run(services -> services.memberships().addMember(company, player))
                .thenApplyAsync(Function.identity(), executor);
    }

    private CompletableFuture<Optional<? extends ICompanyProfile>> profile(Supplier<Optional<SimpleCompany>> company) {
        return CompletableFuture.<Optional<? extends ICompanyProfile>>supplyAsync(
                () -> company.get().flatMap(this::profile), executor);
    }

    private Optional<CompanyProfile> profile(SimpleCompany company) {
        return companyData.retrieveCompanyProfile(company).asFuture().join();
    }
}
