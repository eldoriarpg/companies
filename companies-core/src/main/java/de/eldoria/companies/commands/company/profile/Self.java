/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.profile;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.configuration.elements.companylevel.CompanyLevel;
import de.eldoria.companies.configuration.elements.companylevel.LevelProgress;
import de.eldoria.companies.configuration.elements.companylevel.LevelRequirement;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyProfile;
import de.eldoria.companies.data.wrapper.company.CompanyStats;
import de.eldoria.companies.data.wrapper.order.SimpleOrder;
import de.eldoria.companies.util.Menus;
import de.eldoria.companies.util.Texts;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.localization.MessageComposer;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public class Self extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final Configuration configuration;
    private final Economy economy;
    private final MessageBlocker messageBlocker;

    public Self(
            Plugin plugin,
            ACompanyData companyData,
            AOrderData orderData,
            Configuration configuration,
            Economy economy,
            MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("self").build());
        this.companyData = companyData;
        this.orderData = orderData;
        this.configuration = configuration;
        this.economy = economy;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments)
            throws CommandException {
        companyData
                .retrievePlayerCompanyProfile(player)
                .asFuture()
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return Optional.empty();
                })
                .thenAccept((optProfile) -> {
                    if (optProfile.isEmpty()) {
                        messageSender().sendErrorActionBar(player, "error.noMember");
                        return;
                    }

                    var orders = orderData
                            .retrieveOrdersByCompany(optProfile.get(), OrderState.CLAIMED, OrderState.CLAIMED)
                            .join();
                    var stats = companyData
                            .retrieveCompanyStats(optProfile.get())
                            .asFuture()
                            .join();
                    sendProfile(player, optProfile.get(), orders == null ? List.of() : orders, stats);
                })
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    private void sendProfile(Player player, CompanyProfile profile, List<SimpleOrder> orders, CompanyStats stats) {
        messageBlocker.blockPlayer(player);
        var level = configuration.companySettings().level(profile.level()).orElse(CompanyLevel.DEFAULT);
        var optNextLevel = configuration.companySettings().level(profile.level() + 1);
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
            if (stats != null) nextLevelProgress(composer, nextLevel.requirement(), stats);
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
     */
    private void nextLevelProgress(MessageComposer composer, LevelRequirement requirement, CompanyStats stats) {
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
}
