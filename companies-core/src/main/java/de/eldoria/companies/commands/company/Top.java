/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.company.profile.ProfileCommand;
import de.eldoria.companies.commands.suggest.TopOrderings;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.menus.company.TopMenu;
import de.eldoria.companies.menus.company.TopView;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /company top [page] [ordering]}: the ranking of all companies, one page at a time.
 */
@Subcommand("top")
@PlayerOnly
public final class Top {
    private static final int PAGE_SIZE = 15;
    private static final TopOrder DEFAULT_ORDER = TopOrder.ORDERS;
    private final CommandDependencies deps;
    private final ProfileCommand profiles;

    /**
     * @param deps     what the commands need
     * @param profiles shows the profile of a ranked company
     */
    public Top(CommandDependencies deps, ProfileCommand profiles) {
        this.deps = deps;
        this.profiles = profiles;
    }

    /**
     * Shows the first page by the default ordering.
     *
     * @param sender sender of the command
     */
    @Executes
    public void top(CommandSender sender) {
        top(sender, 1);
    }

    /**
     * Shows a page by the default ordering.
     *
     * @param sender sender of the command
     * @param page   page, starting at 1
     */
    @Executes
    public void top(CommandSender sender, @IntArg(min = 1) int page) {
        renderPage(Senders.player(sender), page, DEFAULT_ORDER);
    }

    /**
     * Shows a page by an ordering.
     *
     * @param sender   sender of the command
     * @param page     page, starting at 1
     * @param ordering ordering, by name
     */
    @Executes
    public void top(CommandSender sender, @IntArg(min = 1) int page, @TopOrderings String ordering) {
        renderPage(Senders.player(sender), page, Checks.enumValue(ordering, TopOrder.class));
    }

    private void renderPage(Player player, int page, TopOrder ordering) {
        var services = deps.services();
        var menu = new TopMenu(deps.messages(), services.economy(), new TopMenu.Navigation() {
            @Override
            public void show(Player viewer, int number, TopOrder order) {
                renderPage(viewer, number, order);
            }

            @Override
            public void profile(Player viewer, int company) {
                profiles.id(viewer, company);
            }
        });
        deps.companyData().retrieveRanking(ordering, page, PAGE_SIZE).whenComplete(ranks -> services.menus()
                .show(player, menu, TopView.of(ordering, page, ranks, PAGE_SIZE)));
    }
}
