package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Name extends EldoCommand {
    private Query query;

    public Name(Query query, Plugin plugin) {
        super(plugin);
        this.query = query;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var playerSearch = query.getPlayerSearch(getPlayerFromSender(sender));

        if (args.length == 0) {
            playerSearch.name(null);
        } else {
            playerSearch.name(String.join(" ", args));
        }
        return true;
    }
}
