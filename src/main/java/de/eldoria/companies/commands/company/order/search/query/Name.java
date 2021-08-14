package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import de.eldoria.eldoutilities.simplecommands.TabCompleteUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class Name extends EldoCommand {
    private final Query query;

    public Name(Plugin plugin, Query query) {
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompleteUtil.completeFreeInput(String.join(" ", args), 32, "order name", localizer());
    }
}
