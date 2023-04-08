/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.company.order;

import de.eldoria.companies.components.order.OrderState;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.data.wrapper.company.CompanyMember;
import de.eldoria.companies.data.wrapper.order.FullOrder;
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

import java.util.Optional;
import java.util.logging.Level;

public class Info extends AdvancedCommand implements IPlayerTabExecutor {
    private final AOrderData orderData;
    private final ACompanyData companyData;
    private final Economy economy;
    private final Configuration configuration;
    private final MessageBlocker messageBlocker;

    public Info(Plugin plugin, ACompanyData companyData, AOrderData orderData, Economy economy, Configuration configuration, MessageBlocker messageBlocker) {
        super(plugin, CommandMeta.builder("info")
                .addArgument("words.id", true)
                .build());
        this.companyData = companyData;
        this.orderData = orderData;
        this.economy = economy;
        this.configuration = configuration;
        this.messageBlocker = messageBlocker;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var id = arguments.asInt(0);
        companyData.retrievePlayerCompanyProfile(player)
                   .asFuture()
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return Optional.empty();
                   })
                   .thenAccept(optProfile -> {
                       if (optProfile.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.noMember");
                           return;
                       }
                       var profile = optProfile.get();
                       var optOrder = orderData.retrieveOrderById(id)
                                               .join();
                       if (optOrder.isEmpty()) {
                           messageSender().sendErrorActionBar(player, "error.unkownOrder");
                           return;
                       }
                       if (optOrder.get()
                                   .company() != profile.id() && optOrder.get()
                                                                         .state() != OrderState.UNCLAIMED) {
                           messageSender().sendErrorActionBar(player, "error.unkownOrder");
                           return;
                       }
                       var order = optOrder.get();
                       var fullOrder = orderData.retrieveFullOrder(order)
                                                .join();
                       renderOrder(player, profile.member(player)
                                                  .get(), fullOrder);
                   })
                   .exceptionally(err -> {
                       plugin().getLogger()
                               .log(Level.SEVERE, "Something went wrong", err);
                       return null;
                   });
    }

    public void renderOrder(Player player, CompanyMember member, FullOrder order) {
        messageBlocker.blockPlayer(player);
        var component = order.companyDetailInfo(member, configuration, economy);
        var composer = MessageComposer.create()
                                      .text(component);
        if (messageBlocker.isBlocked(player)) {
            composer.newLine()
                    .text("<click:run_command:/company chatblock false><red>[x]</red></click>");
        }
        composer.prependLines(25);
        messageBlocker.announce(player, "[x]");
        messageSender().sendMessage(player, composer.build());
    }
}
