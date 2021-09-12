package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.commands.command.AdvancedCommand;
import de.eldoria.eldoutilities.commands.command.CommandMeta;
import de.eldoria.eldoutilities.commands.command.util.Arguments;
import de.eldoria.eldoutilities.commands.command.util.CommandAssertions;
import de.eldoria.eldoutilities.commands.exceptions.CommandException;
import de.eldoria.eldoutilities.commands.executor.IPlayerTabExecutor;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MaterialMatch extends AdvancedCommand implements IPlayerTabExecutor {
    private final Query query;

    public MaterialMatch(Plugin plugin, Query query) {
        super(plugin, CommandMeta
                .builder("material_match")
                .addUnlocalizedArgument("exact|type", true)
                .build());
        this.query = query;
    }

    @Override
    public void onCommand(@NotNull Player player, @NotNull String label, @NotNull Arguments arguments) throws CommandException {
        var matchType = arguments.asString(0);
        CommandAssertions.isTrue(TabCompleteUtil.isCommand(matchType, "exact", "part"), "error.invalidMatchType");
        query.getPlayerSearch(player).exactMatch("exact".equalsIgnoreCase(arguments.asString(0)));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull Player player, @NotNull String alias, @NotNull Arguments arguments) {
        return TabCompleteUtil.complete(arguments.asString(0), "exact", "part");
    }
}
