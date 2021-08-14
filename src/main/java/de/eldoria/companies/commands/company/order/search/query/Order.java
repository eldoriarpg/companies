package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.utils.Parser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Order extends EldoCommand {
    private Query query;

    public Order(Query query, Plugin plugin) {
        super(plugin);
        this.query = query;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "order <asc|desc>")) return false;
        var player = getPlayerFromSender(sender);
        var optional = Parser.parseBoolean(args[0], "asc", "desc");
        if (optional.isEmpty()) {
            messageSender().sendError(player, "Invalid ordering");
            return false;
        }
        query.getPlayerSearch(player).asc(optional.get());
        return true;

    }
}
