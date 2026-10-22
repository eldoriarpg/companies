/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.company.profile.ProfileCommand;
import de.eldoria.companies.commands.suggest.TimeRanges;
import de.eldoria.companies.commands.suggest.TopOrderings;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.components.ranking.TopOrder;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.menus.company.TopMenu;
import de.eldoria.companies.menus.company.TopView;
import de.eldoria.companies.util.FutureCache;
import de.eldoria.companies.util.Threading;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * {@code /company top [page] [ordering] [range]}: the ranking of all companies, one page at a time, all time or over
 * the last 30 or 7 days.
 * <p>
 * Pages and the places of the viewers' companies are cached on this node for {@code top.cacheSeconds}.
 */
@Subcommand("top")
@PlayerOnly
public final class TopCommand {
    private static final int PAGE_SIZE = 15;
    private static final long CACHE_SIZE = 1000;
    private static final TopOrder DEFAULT_ORDER = TopOrder.ORDERS;
    private static final TimeRange DEFAULT_RANGE = TimeRange.ALL;
    private final CommandDependencies deps;
    private final ProfileCommand profiles;
    private final FutureCache<PageKey, List<CompanyRank>> pages;
    private final FutureCache<RankKey, Optional<CompanyRank>> ranks;

    /**
     * @param deps     what the commands need
     * @param profiles shows the profile of a ranked company
     */
    public TopCommand(CommandDependencies deps, ProfileCommand profiles) {
        this.deps = deps;
        this.profiles = profiles;
        var lifetime = deps.configuration().topSettings().cache();
        this.pages = new FutureCache<>(lifetime, CACHE_SIZE);
        this.ranks = new FutureCache<>(lifetime, CACHE_SIZE);
    }

    /**
     * Shows the first page by the default ordering, all time.
     *
     * @param sender sender of the command
     */
    @Executes
    public void top(CommandSender sender) {
        top(sender, 1);
    }

    /**
     * Shows a page by the default ordering, all time.
     *
     * @param sender sender of the command
     * @param page   page, starting at 1
     */
    @Executes
    public void top(CommandSender sender, @IntArg(min = 1) int page) {
        show(Senders.player(sender), page, DEFAULT_ORDER, DEFAULT_RANGE);
    }

    /**
     * Shows a page by an ordering, all time.
     *
     * @param sender   sender of the command
     * @param page     page, starting at 1
     * @param ordering ordering, by name
     */
    @Executes
    public void top(CommandSender sender, @IntArg(min = 1) int page, @TopOrderings String ordering) {
        show(Senders.player(sender), page, Checks.enumValue(ordering, TopOrder.class), DEFAULT_RANGE);
    }

    /**
     * Shows a page by an ordering in a time range.
     *
     * @param sender   sender of the command
     * @param page     page, starting at 1
     * @param ordering ordering, by name
     * @param range    time range, by id
     */
    @Executes
    public void top(
            CommandSender sender, @IntArg(min = 1) int page, @TopOrderings String ordering, @TimeRanges String range) {
        show(Senders.player(sender), page, Checks.enumValue(ordering, TopOrder.class), timeRange(range));
    }

    /**
     * The time range of an id.
     *
     * @param id id as typed
     * @return the range
     * @throws CommandFailure when no range has this id
     */
    static TimeRange timeRange(String id) {
        return TimeRange.byId(id)
                .orElseThrow(() -> CommandFailure.of(
                        "error.invalidEnumValue",
                        Placeholder.unparsed(
                                "values",
                                Arrays.stream(TimeRange.values())
                                        .map(TimeRange::id)
                                        .collect(Collectors.joining(", ")))));
    }

    private void show(Player player, int page, TopOrder ordering, TimeRange range) {
        var services = deps.services();
        var menu = new TopMenu(deps.messages(), services.economy(), new TopMenu.Navigation() {
            @Override
            public void show(Player viewer, int number, TopOrder order, TimeRange timeRange) {
                TopCommand.this.show(viewer, number, order, timeRange);
            }

            @Override
            public void profile(Player viewer, int company) {
                profiles.id(viewer, company);
            }
        });
        var now = LocalDateTime.now();
        var ranking = pages.get(
                new PageKey(ordering, range, page),
                key -> deps.information().stats().retrieveRanking(ordering, range, now, page, PAGE_SIZE));
        var own = ownCompany(player, ordering, range, now);
        ranking.thenCombine(own, (list, company) -> TopView.of(ordering, range, page, list, PAGE_SIZE, company))
                .thenAcceptAsync(view -> services.menus().show(player, menu, view), Threading.mainThread(deps.plugin()))
                .exceptionally(error -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not show the company ranking", error);
                    return null;
                });
    }

    private CompletableFuture<Optional<TopView.OwnCompany>> ownCompany(
            Player player, TopOrder ordering, TimeRange range, LocalDateTime now) {
        return deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .thenCompose(company -> company.map(profile -> ownRank(profile, ordering, range, now))
                        .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
    }

    private CompletableFuture<Optional<TopView.OwnCompany>> ownRank(
            CompanyProfile company, TopOrder ordering, TimeRange range, LocalDateTime now) {
        return ranks.get(
                        new RankKey(company.id(), ordering, range),
                        key -> deps.information().stats().retrieveRank(company.id(), ordering, range, now))
                .thenApply(rank -> Optional.of(TopView.OwnCompany.of(company, rank)));
    }

    private record PageKey(TopOrder ordering, TimeRange range, int page) {}

    private record RankKey(int company, TopOrder ordering, TimeRange range) {}
}
