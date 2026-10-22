/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.commands.suggest.CompanyNames;
import de.eldoria.companies.commands.support.PlayerOnly;
import de.eldoria.companies.commands.support.Senders;
import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.configuration.elements.companylevel.LevelProgress;
import de.eldoria.companies.configuration.elements.companylevel.LevelRequirement;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.util.Menus;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.eldoutilities.messages.MessageSender;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.arguments.StringArgType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

/**
 * {@code /company profile [self|id <id>|name <name>|player <player>]}: the profile of the sender's company, with
 * level progress, or the public profile of another company.
 */
@Subcommand("profile")
@PlayerOnly
public final class ProfileCommand {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public ProfileCommand(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * Shows the profile of the sender's company.
     *
     * @param sender sender of the command
     */
    @Executes
    @Executes("self")
    public void self(CommandSender sender) {
        var player = Senders.player(sender);
        var services = deps.services();
        deps.companyData()
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept((optProfile) -> {
                    if (optProfile.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.noMember");
                        return;
                    }

                    var orders = deps.orderData()
                            .retrieveOrdersByCompany(optProfile.get(), OrderState.CLAIMED, OrderState.CLAIMED)
                            .join();
                    var stats = deps.companyData()
                            .retrieveCompanyStats(optProfile.get())
                            .asFuture()
                            .join();
                    sendProfile(
                            player,
                            optProfile.get(),
                            orders == null ? List.of() : orders,
                            stats,
                            services.messageBlocker(),
                            services.economy());
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Shows the public profile of a company by its id.
     *
     * @param sender sender of the command
     * @param id     id of the company
     */
    @Executes("id")
    public void id(CommandSender sender, int id) {
        var player = Senders.player(sender);
        var messageBlocker = deps.services().messageBlocker();
        deps.companyData()
                .retrieveCompanyById(id)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optComp -> {
                    if (optComp.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.unknownCompany");
                        return;
                    }
                    var optProfile = deps.companyData()
                            .retrieveCompanyProfile(optComp.get())
                            .asFuture()
                            .exceptionally(err -> {
                                deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                                return Optional.empty();
                            })
                            .join();
                    if (optProfile.isEmpty()) return;
                    showExternal(player, optProfile.get(), messageBlocker);
                })
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    /**
     * Shows the public profile of a company by its name.
     *
     * @param sender sender of the command
     * @param name   name of the company
     */
    @Executes("name")
    public void name(CommandSender sender, @CompanyNames @StringArg(StringArgType.GREEDY) String name) {
        var player = Senders.player(sender);
        var messageBlocker = deps.services().messageBlocker();
        deps.companyData()
                .retrieveCompanyByName(name)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optComp -> {
                    if (optComp.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.unknownCompany");
                        return;
                    }
                    var optProfile = deps.companyData()
                            .retrieveCompanyProfile(optComp.get())
                            .asFuture()
                            .join();
                    if (optProfile.isEmpty()) return;
                    messageBlocker.blockPlayer(player);
                    showExternal(player, optProfile.get(), messageBlocker);
                });
    }

    /**
     * Shows the public profile of the company of an online player.
     *
     * @param sender sender of the command
     * @param target player whose company is shown
     */
    @Executes("player")
    public void player(CommandSender sender, Player target) {
        var player = Senders.player(sender);
        var messageBlocker = deps.services().messageBlocker();
        deps.companyData()
                .retrievePlayerCompanyProfile(target)
                .asFuture()
                .exceptionally(err -> {
                    deps.plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept(optCompany -> {
                    if (optCompany.isEmpty()) {
                        messages().sendErrorActionBar(player, "error.noCompany");
                        return;
                    }
                    messageBlocker.blockPlayer(player);
                    showExternal(player, optCompany.get(), messageBlocker);
                });
    }

    private void showExternal(Player player, CompanyProfile profile, MessageBlocker messageBlocker) {
        var builder = MessageComposer.create().text(profile.asExternalProfileComponent(deps.configuration()));
        if (messageBlocker.isBlocked(player)) {
            builder.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        builder.prependLines(25);
        Menus.show(messageBlocker, player, builder);
    }

    private void sendProfile(
            Player player,
            CompanyProfile profile,
            List<SimpleOrder> orders,
            CompanyStats stats,
            MessageBlocker messageBlocker,
            Economy economy) {
        messageBlocker.blockPlayer(player);
        var companySettings = deps.configuration().companySettings();
        var level = companySettings.level(profile.level()).orElse(CompanyLevel.DEFAULT);
        var optNextLevel = companySettings.level(profile.level() + 1);
        var composer = MessageComposer.create()
                .text("<heading>")
                .text(Texts.escape(profile.name()))
                .newLine()
                .text("<name>")
                .localeCode("words.level")
                .text(
                        ": <hover:show_text:'%s'><value>%s - %s</hover>",
                        level.asComponent(), level.level(), level.levelName());
        if (optNextLevel.isPresent()) {
            var nextLevel = optNextLevel.get();
            composer.space()
                    .text("<u><hover:show_text:'%s'><show>", nextLevel.asComponent())
                    .localeCode("company.level.nextLevel")
                    .text("</u></hover>");
            if (stats != null) nextLevelProgress(composer, nextLevel.requirement(), stats, economy);
        }
        composer.newLine()
                .text("<name>")
                .localeCode("words.founded")
                .text(": <value>%s", profile.foundedString())
                .newLine()
                .text("<name>")
                .localeCode("words.leader")
                .text(": <value>%s", profile.owner().player().getName())
                .newLine()
                .text("<name>")
                .localeCode("words.member")
                .text(
                        ": <value>%s <click:run_command:/company member><show>[",
                        profile.members().size())
                .localeCode("words.list")
                .text("]</click>")
                .newLine()
                .text("<name>")
                .localeCode("words.orders")
                .text(": <value>%s <click:run_command:/company order list><show>[", orders.size())
                .localeCode("words.list")
                .text("]</click>");
        if (messageBlocker.isBlocked(player)) {
            composer.newLine().text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        composer.prependLines(25);
        Menus.show(messageBlocker, player, composer);
    }

    /**
     * Adds a line with the company's value, the required value and a bar for each requirement of the next level.
     *
     * @param composer    composer to add the lines to
     * @param requirement requirements of the next level
     * @param stats       current values of the company
     * @param economy     economy formatting the earned money
     */
    private static void nextLevelProgress(
            MessageComposer composer, LevelRequirement requirement, CompanyStats stats, Economy economy) {
        progressLine(
                composer,
                "words.member",
                stats.memberCount(),
                requirement.memberCount(),
                String.valueOf(stats.memberCount()),
                String.valueOf(requirement.memberCount()));
        progressLine(
                composer,
                "level.orderCount",
                stats.orderCount(),
                requirement.orderCount(),
                String.valueOf(stats.orderCount()),
                String.valueOf(requirement.orderCount()));
        progressLine(
                composer,
                "level.deliveredItems",
                stats.deliveredItems(),
                requirement.deliveredItems(),
                String.valueOf(stats.deliveredItems()),
                String.valueOf(requirement.deliveredItems()));
        progressLine(
                composer,
                "level.earnedMoney",
                stats.price(),
                requirement.earnedMoney(),
                economy.format(stats.price()),
                economy.format(requirement.earnedMoney()));
    }

    private static void progressLine(
            MessageComposer composer,
            String label,
            double value,
            double required,
            String valueText,
            String requiredText) {
        composer.newLine()
                .space(2)
                .text("<name>")
                .localeCode(label)
                .text(": <value>%s/%s ", valueText, requiredText)
                .text(Texts.progressBar(LevelProgress.of(value, required)));
    }

    private MessageSender messages() {
        return deps.messages();
    }
}
