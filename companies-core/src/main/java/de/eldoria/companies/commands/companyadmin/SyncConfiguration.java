package de.eldoria.companies.commands.companyadmin;

import de.eldoria.companies.configuration.Configuration;
import de.eldoria.companies.data.repository.ANodeData;
import de.eldoria.companies.util.Permission;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.ITabExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class SyncConfiguration extends AdvancedCommand implements ITabExecutor {
    private final Configuration configuration;
    private final ANodeData nodeData;

    public SyncConfiguration(Plugin plugin, Configuration configuration, ANodeData nodeData) {
        super(plugin, CommandMeta.builder("syncconfiguration")
                .withPermission(Permission.Admin.ADMIN)
                .build());
        this.configuration = configuration;
        this.nodeData = nodeData;
    }

    @Override
    public void onCommand(@NotNull CommandSender sender, @NotNull String alias, @NotNull Arguments args) throws CommandException {
        configuration.syncConfigurations(nodeData)
                     .whenComplete((res, err) -> {
                            messageSender().sendMessage(sender, "companyadmin.syncconfiguration.done");
                     });
    }
}
