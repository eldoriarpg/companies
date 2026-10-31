/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.api;

import de.eldoria.companies.api.write.FoundProblem;
import de.eldoria.companies.api.write.FoundRequest;
import de.eldoria.companies.api.write.FoundResult;
import de.eldoria.companies.api.write.HomeProblem;
import de.eldoria.companies.api.write.HomeRequest;
import de.eldoria.companies.api.write.HomeResult;
import de.eldoria.companies.areas.CompanyHomes;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.components.area.ICompanyArea;
import de.eldoria.companies.components.area.ICompanyHome;
import de.eldoria.companies.components.company.ICompanyProfile;
import de.eldoria.companies.components.company.ICompanyStats;
import de.eldoria.companies.components.company.IMemberStats;
import de.eldoria.companies.components.company.ITreasury;
import de.eldoria.companies.components.join.JoinResult;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.data.repository.AAreaData;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AHomeData;
import de.eldoria.companies.data.repository.AStatsData;
import de.eldoria.companies.data.repository.ATreasuryData;
import de.eldoria.companies.data.wrapper.area.CompanyHome;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.SimpleCompany;
import de.eldoria.companies.services.ActionResult;
import de.eldoria.companies.util.Times;
import net.kyori.adventure.text.Component;
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
 * Reads companies from the repositories; adds members, founds companies and sets homes through the services the
 * commands use. Refusals are told apart by the message key the service refused with.
 */
final class CompanyApiImpl implements CompanyApi {
    private final ACompanyData companyData;
    private final AStatsData stats;
    private final ATreasuryData treasuries;
    private final AAreaData areas;
    private final AHomeData homes;
    private final WriteAccess writes;
    private final Function<UUID, OfflinePlayer> players;
    private final Function<CommandFailure, Component> messages;
    private final Supplier<LocalDateTime> clock;
    private final Executor executor;

    CompanyApiImpl(
            ACompanyData companyData,
            AStatsData stats,
            ATreasuryData treasuries,
            AAreaData areas,
            AHomeData homes,
            WriteAccess writes,
            Function<UUID, OfflinePlayer> players,
            Function<CommandFailure, Component> messages,
            Supplier<LocalDateTime> clock,
            Executor executor) {
        this.companyData = companyData;
        this.stats = stats;
        this.treasuries = treasuries;
        this.areas = areas;
        this.homes = homes;
        this.writes = writes;
        this.players = players;
        this.messages = messages;
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
                                company, Times.start(range, clock.get()).orElse(null))
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
    public CompletableFuture<List<? extends ICompanyArea>> areas(int company) {
        return CompletableFuture.<List<? extends ICompanyArea>>supplyAsync(() -> areas.ofCompany(company), executor);
    }

    @Override
    public CompletableFuture<Optional<? extends ICompanyHome>> home(int company) {
        return CompletableFuture.<Optional<? extends ICompanyHome>>supplyAsync(() -> homes.home(company), executor);
    }

    @Override
    public CompletableFuture<JoinResult> addMember(int company, UUID player) {
        return writes.run(services -> services.memberships().addMember(company, player))
                .thenApplyAsync(Function.identity(), executor);
    }

    @Override
    public CompletableFuture<FoundResult> found(FoundRequest request) {
        return writes.run(services -> services.companies().found(request.owner(), request.name(), request.charge()))
                .thenApplyAsync(this::founded, executor);
    }

    private FoundResult founded(ActionResult<Integer> result) {
        return switch (result) {
            case ActionResult.Done<Integer>(var id) ->
                new FoundResult.Founded(companyData
                        .retrieveCompanyById(id)
                        .asFuture()
                        .join()
                        .flatMap(this::profile)
                        .orElseThrow(() -> new IllegalStateException("The founded company " + id + " is gone")));
            case ActionResult.Refused<Integer>(var failure) ->
                new FoundResult.Rejected(foundProblem(failure.localeKey()), messages.apply(failure));
        };
    }

    private static FoundProblem foundProblem(String key) {
        return switch (key) {
            case "error.invalidLength" -> FoundProblem.INVALID_NAME;
            case "error.companyNameUsed" -> FoundProblem.NAME_TAKEN;
            case "error.alreadyMember" -> FoundProblem.ALREADY_MEMBER;
            case "error.insufficientCurrency" -> FoundProblem.NO_MONEY;
            case "error.cancelledByIntegration" -> FoundProblem.CANCELLED;
            default -> FoundProblem.FAILED;
        };
    }

    @Override
    public CompletableFuture<HomeResult> setHome(HomeRequest request) {
        return writes.run(services -> CompletableFuture.supplyAsync(
                        () -> services.homes().spot(request.location(), request.by()), services.mainThread())
                .thenApplyAsync(spot -> setHome(services.homes(), request.company(), spot), executor));
    }

    private HomeResult setHome(CompanyHomes homes, int company, CompanyHomes.Spot spot) {
        var profile = companyData.retrieveCompanyById(company).asFuture().join().flatMap(this::profile);
        if (profile.isEmpty()) {
            return new HomeResult.Rejected(
                    HomeProblem.NO_COMPANY, messages.apply(CommandFailure.of("company.home.error.noCompany")));
        }
        return switch (homes.set(profile.get(), spot, false)) {
            case ActionResult.Done<CompanyHome>(var home) -> new HomeResult.Stored(home);
            case ActionResult.Refused<CompanyHome>(var failure) ->
                new HomeResult.Rejected(
                        CompanyHomes.OUTSIDE.equals(failure.localeKey()) ? HomeProblem.NOT_IN_AREA : HomeProblem.FAILED,
                        messages.apply(failure));
        };
    }

    private CompletableFuture<Optional<? extends ICompanyProfile>> profile(Supplier<Optional<SimpleCompany>> company) {
        return CompletableFuture.<Optional<? extends ICompanyProfile>>supplyAsync(
                () -> company.get().flatMap(this::profile), executor);
    }

    private Optional<CompanyProfile> profile(SimpleCompany company) {
        return companyData.retrieveCompanyProfile(company).asFuture().join();
    }
}
