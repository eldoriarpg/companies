package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.eldoutilities.utils.Parser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Price extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;

    public Price(Plugin plugin, Query query) {
        super(plugin, CommandMeta.builder("min_price")
                .addAlias("max_price")
                .addArgument("price", true)
                .build());
        this.query = query;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        if ("min_price".equalsIgnoreCase(label)) {
            query.getPlayerSearch(player).minPrice(arguments.asDouble(0));
            return;
        }
        query.getPlayerSearch(player).maxPrice(arguments.asDouble(0));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        if (arguments.asString(0).isEmpty()) {
            return Collections.singletonList("price");
        }
        return TabCompleteUtil.completeMinDouble(arguments.asString(0), 1.0, localizer());
    }
}
