/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.commands.companyadmin.CalcLevel;
import de.eldoria.companies.commands.companyadmin.Delete;
import de.eldoria.companies.commands.companyadmin.Level;
import de.eldoria.companies.commands.companyadmin.Reload;
import de.eldoria.companies.commands.companyadmin.Rename;
import de.eldoria.companies.commands.companyadmin.SyncConfiguration;
import de.eldoria.companies.commands.companyadmin.TransferOwner;
import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.services.LevelService;
import de.eldoria.companies.updates.Updates;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.defaultcommands.DefaultDebug;
import de.eldoria.messageblocker.blocker.MessageBlocker;
import org.bukkit.plugin.Plugin;

public class CompanyAdmin extends AdvancedCommand {
    public CompanyAdmin(
            Plugin plugin,
            Configuration configuration,
            ACompanyData companyData,
            AOrderData orderData,
            MessageBlocker messageBlocker,
            LevelService levelService,
            ANodeData nodeData,
            ItemReturnService itemReturnService,
            Updates updates) {
        super(
                plugin,
                CommandMeta.builder("companyadmin")
                        .withSubCommand(new Level(plugin, configuration, messageBlocker))
                        .withSubCommand(new CalcLevel(plugin, levelService))
                        .withSubCommand(new Delete(plugin, companyData, orderData, itemReturnService))
                        .withSubCommand(new Reload(plugin, configuration, updates))
                        .withSubCommand(new TransferOwner(plugin, companyData))
                        .withSubCommand(new Rename(plugin, companyData))
                        .withSubCommand(new DefaultDebug(plugin, Permission.Admin.ADMIN))
                        .withSubCommand(new SyncConfiguration(plugin, configuration, nodeData))
                        .build());
    }
}
