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
import java.util.Locale;

public class Size extends EldoCommand {
    private final Query query;

    public Size(Plugin plugin, Query query) {
        super(plugin);
        this.query = query;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var player = getPlayerFromSender(sender);
        if (argumentsInvalid(sender, args, 1, label.toLowerCase(Locale.ROOT) + " <num>")) return true;
        var size = Parser.parseInt(args[0]);
        if (size.isEmpty()) {
            messageSender().sendError(player, "invalidNumer");
            return false;
        }
        if ("max_size".equalsIgnoreCase(label)) {
            query.getPlayerSearch(player).maxOrderSize(size.getAsInt());
            return true;
        }
        query.getPlayerSearch(player).minOrderSize(size.getAsInt());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args[0].isEmpty()) {
            return Collections.singletonList("size");
        }
        return TabCompleteUtil.completeInt(args[0], 1, 1000000, localizer());
    }
}
