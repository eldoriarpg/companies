/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.commands.CommandDependencies;
import de.eldoria.companies.util.Permission.Admin;
import net.strokkur.commands.Executes;
import net.strokkur.commands.Subcommand;
import net.strokkur.commands.permission.Permission;
import org.bukkit.command.CommandSender;

/**
 * {@code /companyadmin syncconfiguration}: stores the shared files of this node as their next revisions, each only
 * when it changed, or takes the latest revisions, depending on the node type. Afterwards everything registered with
 * {@link CommandDependencies#onReload(Runnable)} applies the configuration, as after a reload. The secondaries hear of
 * a new revision over the node bus, or find it within half a minute without a realtime bus. Since it reloads, it needs
 * the permission of {@code /companyadmin reload}.
 */
@Subcommand("syncconfiguration")
@Permission(Admin.RELOAD)
public final class SyncConfiguration {
    private final CommandDependencies deps;

    /**
     * @param deps what the commands need
     */
    public SyncConfiguration(CommandDependencies deps) {
        this.deps = deps;
    }

    /**
     * @param sender sender of the command
     */
    @Executes
    public void sync(CommandSender sender) {
        deps.requireReady();
        deps.configuration()
                .syncConfigurations(deps.nodeData(), sender.getName())
                .whenComplete((res, err) -> {
                    if (err == null) {
                        deps.adminLog().entry(sender, "syncconfiguration").write();
                        deps.plugin().getServer().getScheduler().runTask(deps.plugin(), deps::reloaded);
                    }
                    deps.messages().sendMessage(sender, "companyadmin.syncconfiguration.done");
                });
    }
}
