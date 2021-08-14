package de.eldoria.companies.commands.company.order.search.query;

import de.eldoria.companies.commands.company.order.search.Query;
import de.eldoria.eldoutilities.simplecommands.EldoCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class Material extends EldoCommand {
    private Query query;

    public Material(Query query, Plugin plugin) {
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
}
