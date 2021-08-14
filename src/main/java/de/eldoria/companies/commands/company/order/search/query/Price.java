package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import de.eldoria.eldoutilities.utils.Parser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Price extends EldoCommand {
    private final Query query;

    public Price(Plugin plugin, Query query) {
        super(plugin);
        this.query = query;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (argumentsInvalid(sender, args, 1, "max_price <num>")) return false;
        var player = getPlayerFromSender(sender);
        var price = Parser.parseDouble(args[0]);
        if (price.isEmpty()) {
            messageSender().sendError(player, "invalidNumer");
            return false;
        }

        if ("min_price".equalsIgnoreCase(label)) {
            query.getPlayerSearch(player).minPrice(price.getAsDouble());
            return true;
        }
        query.getPlayerSearch(player).maxPrice(price.getAsDouble());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args[0].isEmpty()) {
            return Collections.singletonList("price");
        }
        return TabCompleteUtil.completeDouble(args[0], 1.0, 1000000.0, localizer());
    }
}
