package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class Name extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;

    public Name(Plugin plugin, Query query) {
        super(plugin, CommandMeta.builder("name")
                .addArgument("words.name", false)
                .build());
        this.query = query;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var playerSearch = query.getPlayerSearch(player);
        if (arguments.isEmpty()) {
            playerSearch.name(null);
        } else {
            playerSearch.name(arguments.join());
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return TabCompleteUtil.completeFreeInput(arguments.join(), 32, localizer().localize("words.defaultOrderName"), localizer());
    }
}
