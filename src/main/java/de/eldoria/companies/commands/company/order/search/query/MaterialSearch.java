package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MaterialSearch extends EldoCommand {
    private final Query query;

    public MaterialSearch(Plugin plugin, Query query) {
        super(plugin);
        this.query = query;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "material_search <any|all>")) return false;
        var matchType = args[0];
        if(!("all".equalsIgnoreCase(matchType) || "any".equalsIgnoreCase(matchType))) {
            messageSender().sendError(sender, "Invalid search type");
            return false;
        }
        query.getPlayerSearch(getPlayerFromSender(sender)).anyMaterial("any".equalsIgnoreCase(args[0]));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompleteUtil.complete(args[0], "any", "all");
    }
}
