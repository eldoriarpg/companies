/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.commands.company.Chatblock;
import de.eldoria.companies.commands.company.Create;
import de.eldoria.companies.commands.company.Disband;
import de.eldoria.companies.commands.company.Invite;
import de.eldoria.companies.commands.company.Kick;
import de.eldoria.companies.commands.company.Leave;
import de.eldoria.companies.commands.company.Member;
import de.eldoria.companies.commands.company.Order;
import de.eldoria.companies.commands.company.Permission;
import de.eldoria.companies.commands.company.Profile;
import de.eldoria.companies.commands.company.Rename;
import de.eldoria.companies.commands.company.Returns;
import de.eldoria.companies.commands.company.Top;
import de.eldoria.companies.commands.company.Transfer;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.services.DisbandService;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.notifications.NotificationService;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.Plugin;

public class Company extends AdvancedCommand {
    public Company(
            Plugin plugin,
            ACompanyData companyData,
            AOrderData orderData,
            Economy economy,
            Configuration configuration,
            MessageBlocker messageBlocker,
            ItemReturnService itemReturnService,
            NotificationService notificationService,
            DisbandService disbandService) {
        super(plugin);
        var profile = new Profile(plugin, companyData, orderData, configuration, economy, messageBlocker);
        var meta = CommandMeta.builder("company")
                .withDefaultCommand(profile)
                .withSubCommand(profile)
                .withSubCommand(new Create(plugin, companyData, economy, configuration))
                .withSubCommand(new Invite(plugin, companyData, configuration))
                .withSubCommand(new Kick(plugin, companyData))
                .withSubCommand(new Leave(plugin, companyData))
                .withSubCommand(new Transfer(plugin, companyData, notificationService))
                .withSubCommand(new Disband(plugin, companyData, disbandService))
                .withSubCommand(new Member(plugin, companyData, messageBlocker))
                .withSubCommand(new Order(
                        plugin, companyData, orderData, economy, configuration, messageBlocker, itemReturnService))
                .withSubCommand(new Permission(plugin, companyData, messageBlocker))
                .withSubCommand(new Top(plugin, companyData, economy, messageBlocker))
                .withSubCommand(new Rename(plugin, configuration, economy, companyData))
                .withSubCommand(new Chatblock(plugin, messageBlocker))
                .withSubCommand(new Returns(plugin, itemReturnService))
                .build();
        meta(meta);
    }
}
