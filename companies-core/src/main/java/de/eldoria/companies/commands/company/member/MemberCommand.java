/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.member;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.MemberSortings;
import de.eldoria.companies.commands.suggest.StatRanges;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.CommandFailure;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.ranking.TimeRange;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.menus.company.MemberContributions;
import de.eldoria.companies.menus.company.MemberMenu;
import de.eldoria.companies.menus.company.MemberSorting;
import de.eldoria.companies.menus.company.MemberView;
import de.eldoria.companies.util.Threading;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * {@code /company member [self|id <id>|<sorting> [range]]}: the members of the sender's company with their permissions
 * and contributions, sortable by a contribution of all time or the last 30 days, or the members of another company,
 * which show neither.
 */
@Subcommand("member")
@PlayerOnly
public final class MemberCommand {
    private final CommandDependencies deps;
    private final BiConsumer<Player, String> permissions;

    /**
     * @param deps        what the commands need
     * @param permissions opens the permission editor for a member, by name
     */
    public MemberCommand(CommandDependencies deps, BiConsumer<Player, String> permissions) {
        this.deps = deps;
        this.permissions = permissions;
    }

    /**
     * Lists the members of the sender's company, with buttons to edit their permissions for those allowed to.
     *
     * @param sender sender of the command
     */
    @Executes
    @Executes("self")
    public void self(CommandSender sender) {
        self(Senders.player(sender), MemberSorting.NAME, TimeRange.ALL);
    }

    /**
     * Lists the members of the sender's company sorted by their contribution of all time.
     *
     * @param sender  sender of the command
     * @param sorting what to sort by
     */
    @Executes
    public void self(CommandSender sender, @MemberSortings String sorting) {
        self(Senders.player(sender), Checks.enumValue(sorting, MemberSorting.class), TimeRange.ALL);
    }

    /**
     * Lists the members of the sender's company sorted by their contribution in a range.
     *
     * @param sender  sender of the command
     * @param sorting what to sort by
     * @param range   {@code all} or {@code 30d}
     */
    @Executes
    public void self(CommandSender sender, @MemberSortings String sorting, @StatRanges String range) {
        self(Senders.player(sender), Checks.enumValue(sorting, MemberSorting.class), range(range));
    }

    /**
     * @param input entered range
     * @return the range of member contributions
     * @throws CommandFailure when the input is no range of member contributions
     */
    static TimeRange range(String input) {
        return TimeRange.byId(input)
                .filter(MemberContributions.RANGES::contains)
                .orElseThrow(() -> CommandFailure.of(
                        "error.invalidEnumValue",
                        Placeholder.unparsed(
                                "values",
                                MemberContributions.RANGES.stream()
                                        .map(TimeRange::id)
                                        .collect(Collectors.joining(", ")))));
    }

    private void self(Player player, MemberSorting sorting, TimeRange range) {
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optProfile -> {
                    if (optProfile.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }
                    show(player, optProfile.get(), sorting, range);
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Lists the members of a company by its id.
     *
     * @param sender sender of the command
     * @param id     id of the company
     */
    @Executes("id")
    public void id(CommandSender sender, int id) {
        var player = Senders.player(sender);
        deps.companyData()
                .retrieveCompanyById(id)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optSimple -> {
                    if (optSimple.isEmpty()) {
                        deps.messages().sendErrorActionBar(player, "error.unknownCompany");
                        return;
                    }
                    deps.companyData()
                            .retrieveCompanyProfile(optSimple.get())
                            .asFuture()
                            .exceptionally(err -> {
                                deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                                return Optional.empty();
                            })
                            .join()
                            .ifPresent(profile -> show(player, profile));
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Shows the members of a company sorted by name.
     *
     * @param player  viewing player
     * @param profile the company
     */
    public void show(Player player, CompanyProfile profile) {
        show(player, profile, MemberSorting.NAME, TimeRange.ALL);
    }

    /**
     * Shows the members of a company. Members of the company also see what every member contributed, all time and in
     * the last 30 days.
     *
     * @param player  viewing player
     * @param profile the company
     * @param sorting what to sort by
     * @param range   range of the contributions to sort by
     */
    private void show(Player player, CompanyProfile profile, MemberSorting sorting, TimeRange range) {
        var services = deps.services();
        var menu = new MemberMenu(deps.messages(), services.economy(), services.menus(), permissions);
        var view = MemberView.of(profile, player);
        contributions(view, profile.id())
                .thenAcceptAsync(
                        shown -> services.menus().show(player, menu, MemberMenu.Model.of(shown, sorting, range)),
                        Threading.mainThread(deps.plugin()))
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Could not show the members", err);
                    return null;
                });
    }

    private CompletableFuture<MemberView> contributions(MemberView view, int company) {
        if (!view.showPermissions()) return CompletableFuture.completedFuture(view);
        var stats = deps.information().stats();
        var since = TimeRange.LAST_30_DAYS.start(LocalDateTime.now()).orElseThrow();
        return stats.retrieveMemberStats(company, null)
                .thenCombine(stats.retrieveMemberStats(company, since), MemberContributions::of)
                .thenApply(view::withContributions);
    }
}
