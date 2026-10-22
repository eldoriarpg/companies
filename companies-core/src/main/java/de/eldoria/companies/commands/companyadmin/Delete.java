/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.data.repository.ACompanyData;
import de.eldoria.companies.data.repository.AOrderData;
import de.eldoria.companies.events.company.CompanyDisbandEvent;
import de.eldoria.companies.services.ItemReturnService;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.Completion;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.logging.Level;

public class Delete extends AdvancedCommand implements IPlayerTabExecutor {
    private final ACompanyData companyData;
    private final AOrderData orderData;
    private final ItemReturnService itemReturnService;

    public Delete(Plugin plugin, ACompanyData companyData, AOrderData orderData, ItemReturnService itemReturnService) {
        super(
                plugin,
                CommandMeta.builder("delete")
                        .withPermission(Permission.Admin.DELETE)
                        .addArgument("words.name", true)
                        .build());
        this.companyData = companyData;
        this.orderData = orderData;
        this.itemReturnService = itemReturnService;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String alias, @NotNull Arguments args)
            throws CommandException {
        var name = args.join();

        companyData
                .retrieveCompanyByName(name)
                .asFuture()
                .thenAccept(company -> {
                    if (company.isEmpty()) {
                        messageSender().sendError(player, "error.unknownCompany");
                        return;
                    }

                    var profile =
                            companyData.retrieveCompanyProfile(company.get()).join();
                    var workers =
                            orderData.submitCompanyOrdersPurge(company.get()).join();
                    if (workers != null) {
                        itemReturnService.notifyWorkers(workers);
                    }
                    if (!Boolean.TRUE.equals(
                            companyData.submitCompanyPurge(company.get()).join())) {
                        messageSender().sendError(player, "error.saveFailed");
                        return;
                    }
                    messageSender().sendMessage(player, "company.leave.disbanded");
                    profile.ifPresent(disbanded ->
                            plugin().getServer().getPluginManager().callEvent(new CompanyDisbandEvent(disbanded)));
                })
                .exceptionally(err -> {
                    plugin().getLogger().log(Level.SEVERE, "Something went wrong", err);
                    return null;
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull Player player, @NotNull String alias, @NotNull Arguments args) {
        return Completion.completeFreeInput(args.join(), 32, localizer().localize("words.name"));
    }
}
