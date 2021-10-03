package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Clear extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;

    public Clear(Plugin plugin, Query query) {
        super(plugin, CommandMeta.builder("clear").build());
        this.query = query;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        query.reset(player);
    }
}
