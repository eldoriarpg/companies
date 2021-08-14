package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.companies.commands.company.order.search.SortingType;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.EnumUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Sorting extends EldoCommand {
    private Query query;

    public Sorting(Query query, Plugin plugin) {
        super(plugin);
        this.query = query;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "sorting <sorting>")) return false;
        var player = getPlayerFromSender(sender);
        var parse = EnumUtil.parse(args[0], SortingType.class);
        if (parse == null) {
            messageSender().sendError(player, "Invalid sorting type");
            return false;
        }
        query.getPlayerSearch(player).sorting(parse);
        return true;

    }
}
