/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.TopOrderings;
import de.eldoria.companies.commands.support.Checks;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.data.wrapper.company.CompanyRank;
import de.eldoria.companies.util.Colors;
import de.eldoria.companies.util.Menus;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.localization.MessageComposer;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.IntArg;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * {@code /company top [page] [ordering]}: the ranking of all companies, one page at a time.
 */
@Subcommand("top")
@PlayerOnly
public final class Top {
    private static final int PAGE_SIZE = 15;
    private static final TopOrder DEFAULT_ORDER = TopOrder.ORDERS;
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public Top(CommandDependencies deps) {
        this.deps = deps;
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

    private void renderPage(Player player, int page, TopOrder orders) {
        var services = deps.services();
        deps.companyData()
                .retrieveRanking(orders, page, PAGE_SIZE)
                .whenComplete(companyRanks -> sendPage(player, page, orders, companyRanks, services));
    }

    private void sendPage(
            Player player, int page, TopOrder order, List<CompanyRank> ranks, CommandDependencies.Services services) {
        var messageBlocker = services.messageBlocker();
        messageBlocker.blockPlayer(player);
        var composer = MessageComposer.create()
                .text("<heading>")
                .localeCode("company.top.ranking")
                .newLine()
                .text("<name>")
                .localeCode("words.ordering")
                .text(": ");
        for (var value : TopOrder.values()) {
            composer.text(
                            "<click:run_command:/company top %s %s><%s>[",
                            page, value.name(), Colors.active(order == value))
                    .localeCode(value.translationKey())
                    .text("]</click>");
        }
        composer.newLine();
        for (var rank : ranks) {
            composer.text(
                            "<name>%s | <value><hover:show_text:'%s'><gold>%s</hover>",
                            rank.rank(),
                            Texts.argument(rank.asComponent(services.economy())),
                            Texts.escape(rank.name()))
                    .newLine();
        }
        if (page > 1) {
            composer.text(
                    "<click:run_command:/company top %s %s><active>%s</click>",
                    page - 1, order.name(), Texts.LEFT_ARROW);
        } else {
            composer.text("<inactive>%s", Texts.LEFT_ARROW);
        }
        composer.localeCode("words.page").text(" <aqua>%s ", page);

        if (ranks.size() < PAGE_SIZE) {
            composer.text("<inactive>%s", Texts.RIGHT_ARROW);
        } else {
            composer.text(
                    "<click:run_command:/company top %s %s><active>%s</click>",
                    page + 1, order.name(), Texts.RIGHT_ARROW);
        }
        if (messageBlocker.isBlocked(player)) {
            composer.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        composer.prependLines(25);
        Menus.show(messageBlocker, player, composer);
    }
}
