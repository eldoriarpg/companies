/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands;

import de.eldoria.companies.commands.companyadmin.CalcLevel;
import de.eldoria.companies.commands.companyadmin.DashboardAdminCommand;
import de.eldoria.companies.commands.companyadmin.Delete;
import de.eldoria.companies.commands.companyadmin.EventCommand;
import de.eldoria.companies.commands.companyadmin.JobAdminCommand;
import de.eldoria.companies.commands.companyadmin.NodeCommand;
import de.eldoria.companies.commands.companyadmin.OffersCommand;
import de.eldoria.companies.commands.companyadmin.Reload;
import de.eldoria.companies.commands.companyadmin.Rename;
import de.eldoria.companies.commands.companyadmin.SettingsCommand;
import de.eldoria.companies.commands.companyadmin.SetupCommand;
import de.eldoria.companies.commands.companyadmin.SyncConfiguration;
import de.eldoria.companies.commands.companyadmin.SystemOrdersCommand;
import de.eldoria.companies.commands.companyadmin.TransferOwner;
import de.eldoria.companies.commands.companyadmin.TreasuryCommand;
import de.eldoria.companies.commands.companyadmin.board.BoardAdminCommand;
import de.eldoria.companies.commands.companyadmin.grant.GrantCommand;
import de.eldoria.companies.commands.companyadmin.level.LevelCommand;
import de.eldoria.companies.commands.companyadmin.order.AdminOrderCommand;
import de.eldoria.companies.commands.companyadmin.player.AdminPlayerCommand;
import de.eldoria.companies.commands.support.AdminAccess;
import de.eldoria.companies.commands.support.DebugAccess;
import de.eldoria.companies.commands.support.HandleFailures;
import de.eldoria.companies.debug.CompaniesDebugSettings;
import de.eldoria.eldoutilities.debug.DebugUtil;
import net.strokkur.commands.Command;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.paper.Description;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin}: administration of the plugin. Every subcommand has its own permission.
 */
@Command("companyadmin")
@Description("Manage the plugin")
@HandleFailures
@AdminAccess
final class CompanyAdminCommand {
    private final CommandDependencies deps;

    @Subcommand
    final LevelCommand level;

    @Subcommand
    final CalcLevel calcLevel;

    @Subcommand
    final Delete delete;

    @Subcommand
    final Reload reload;

    @Subcommand
    final TransferOwner transferOwner;

    @Subcommand
    final Rename rename;

    @Subcommand
    final SyncConfiguration syncConfiguration;

    @Subcommand
    final AdminOrderCommand order;

    @Subcommand
    final AdminPlayerCommand player;

    @Subcommand
    final GrantCommand grant;

    @Subcommand
    final BoardAdminCommand board;

    @Subcommand
    final TreasuryCommand treasury;

    @Subcommand
    final NodeCommand node;

    @Subcommand
    final SettingsCommand settings;

    @Subcommand
    final SetupCommand setup;

    @Subcommand
    final DashboardAdminCommand dashboard;

    @Subcommand
    final SystemOrdersCommand systemOrders;

    @Subcommand
    final OffersCommand offers;

    @Subcommand
    final EventCommand event;

    @Subcommand
    final JobAdminCommand job;

    CompanyAdminCommand(CommandDependencies deps) {
        this.deps = deps;
        level = new LevelCommand(deps);
        calcLevel = new CalcLevel(deps);
        delete = new Delete(deps);
        reload = new Reload(deps);
        transferOwner = new TransferOwner(deps);
        rename = new Rename(deps);
        syncConfiguration = new SyncConfiguration(deps);
        order = new AdminOrderCommand(deps);
        player = new AdminPlayerCommand(deps, order);
        grant = new GrantCommand(deps);
        board = new BoardAdminCommand(deps);
        treasury = new TreasuryCommand(deps);
        node = new NodeCommand(deps);
        settings = new SettingsCommand(deps);
        setup = new SetupCommand(deps);
        dashboard = new DashboardAdminCommand(deps);
        systemOrders = new SystemOrdersCommand(deps);
        offers = new OffersCommand(deps);
        event = new EventCommand(deps);
        job = new JobAdminCommand(deps);
    }

    /**
     * Creates a debug report.
     *
     * @param sender sender of the command
     */
    @Executes("debug")
    @DebugAccess
    void debug(CommandSender sender) {
        DebugUtil.dispatchDebug(sender, deps.plugin(), CompaniesDebugSettings.create());
    }
}
