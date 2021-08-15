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
import java.util.Set;

public class Material extends EldoCommand {
    private final Query query;

    public Material(Plugin plugin, Query query) {
        super(plugin);
        this.query = query;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        var player = getPlayerFromSender(sender);
        if ("material_add".equalsIgnoreCase(label)) {
            if (argumentsInvalid(sender, args, 1, "material_add <material>")) return false;
            query.getPlayerSearch(player).materials().add(String.join("_", args));
            return true;
        }

        var search = query.getPlayerSearch(player);
        if (args.length == 0) {
            search.materials().clear();
            return true;
        }
        search.materials().remove(String.join("_", args));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompleteUtil.completeMaterial(String.join("_", args), true);
    }
}
